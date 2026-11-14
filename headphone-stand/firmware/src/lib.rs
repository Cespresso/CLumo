pub mod ams;
pub mod connection;
pub mod controls;
pub mod volume;
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn invalid_payloads() {
        assert_eq!(parse(3, &[15]), Some(Command::Brightness(15)));
        for data in [&[][..], &[16], &[1, 2]] {
            assert_eq!(parse(3, data), None);
        }
        assert_eq!(parse(4, &[0; 7]), None);
        assert_eq!(parse(5, &[9; 8]), None);
        assert_eq!(parse(6, &[3]), None);
        assert_eq!(parse(7, &[101]), None);
        assert_eq!(parse(7, &[255]), None);
    }
    #[test]
    fn volume_boundaries_without_transport_expiry() {
        let mut s = State::default();
        for (v, n) in [
            (0, 0),
            (1, 1),
            (33, 1),
            (34, 2),
            (66, 2),
            (67, 3),
            (99, 3),
            (100, 3),
            (255, 0),
        ] {
            s.set_volume(volume::Percent::new(v));
            assert_eq!(s.leds(2099), n);
            assert_eq!(s.leds(2100), n);
        }
    }
    #[test]
    fn display_priority_expiry_recovery() {
        let mut s = State::default();
        s.apply(Command::Visualizer([8; 8]), 0);
        assert_eq!(s.frame(0), [255; 8]);
        s.action(Action::Next, 100);
        assert_ne!(s.frame(599), [255; 8]);
        assert_eq!(s.frame(600), [255; 8]);
        assert_eq!(s.frame(2000), s.settings.frame);
        s.apply(Command::Visualizer([1, 0, 0, 0, 0, 0, 0, 0]), 2100);
        assert_eq!(s.frame(2100), [0, 0, 0, 0, 0, 0, 0, 128]);
    }
    #[test]
    fn button_bounce_hold() {
        let mut b = Button::default();
        assert!(!b.update(true, 0));
        assert!(!b.update(false, 5));
        assert!(!b.update(true, 10));
        assert!(!b.update(true, 29));
        assert!(b.update(true, 30));
        assert!(!b.update(true, 1000));
    }
    #[test]
    fn encoder_detent() {
        let mut e = Encoder::new(0);
        assert_eq!([1, 3, 2, 0].map(|v| e.update(v)), [0, 0, 0, 1]);
        assert_eq!([2, 3, 1, 0].map(|v| e.update(v)), [0, 0, 0, -1]);
        assert_eq!(e.update(3), 0);
        assert_eq!(e.update(0), 0);
    }
    #[test]
    fn hid_release_disconnect() {
        let mut h = Hid::default();
        assert_eq!(h.input(Action::Next, false, 0), None);
        assert_eq!(h.tick(true, 20), None);
        assert_eq!(h.input(Action::Next, true, 30), Some([0xb5, 0]));
        assert_eq!(h.tick(true, 49), None);
        assert_eq!(h.tick(true, 50), Some([0, 0]));
        h.input(Action::Previous, true, 60);
        assert_eq!(h.tick(false, 61), None);
        assert_eq!(h.tick(true, 100), None);
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Command {
    Brightness(u8),
    Frame([u8; 8]),
    Visualizer([u8; 8]),
    Standby(u8),
}
pub fn parse(id: u8, data: &[u8]) -> Option<Command> {
    match (id, data) {
        (3, [v]) if *v <= 15 => Some(Command::Brightness(*v)),
        (4, d) if d.len() == 8 => Some(Command::Frame(d.try_into().ok()?)),
        (5, d) if d.len() == 8 && d.iter().all(|v| *v <= 8) => {
            Some(Command::Visualizer(d.try_into().ok()?))
        }
        (6, [v]) if *v <= 2 => Some(Command::Standby(*v)),
        _ => None,
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Animation {
    pub frames: [[u8; 8]; 8],
    pub count: u8,
    pub period_ms: u16,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Settings {
    pub brightness: u8,
    pub frame: [u8; 8],
    pub standby: u8,
    pub animation: Option<Animation>,
}
impl Settings {
    pub const ENCODED_LEN: usize = 78;
    pub fn encode(self) -> [u8; Self::ENCODED_LEN] {
        let mut b = [0; Self::ENCODED_LEN];
        b[0] = self.brightness;
        b[1] = self.standby;
        b[2..10].copy_from_slice(&self.frame);
        b[10] = 1;
        if let Some(a) = self.animation {
            b[11] = a.count;
            b[12..14].copy_from_slice(&a.period_ms.to_le_bytes());
            for (i, frame) in a.frames.iter().enumerate() {
                b[14 + i * 8..22 + i * 8].copy_from_slice(frame);
            }
        }
        b
    }
    pub fn decode(b: &[u8]) -> Option<Self> {
        if !matches!(b.len(), 10 | Self::ENCODED_LEN) || b[0] > 15 || b[1] > 2 {
            return None;
        }
        let mut s = Self {
            brightness: b[0],
            standby: b[1],
            frame: b[2..10].try_into().ok()?,
            animation: None,
        };
        if b.len() == Self::ENCODED_LEN {
            if b[10] != 1 || b[11] > 8 {
                return None;
            }
            if b[11] > 0 {
                let period_ms = u16::from_le_bytes([b[12], b[13]]);
                if !(100..=2000).contains(&period_ms) {
                    return None;
                }
                let mut frames = [[0; 8]; 8];
                for (i, frame) in frames.iter_mut().enumerate() {
                    frame.copy_from_slice(&b[14 + i * 8..22 + i * 8]);
                }
                if frames[0] != s.frame {
                    return None;
                }
                s.animation = Some(Animation {
                    frames,
                    count: b[11],
                    period_ms,
                });
            }
        }
        Some(s)
    }
    pub fn idle_frame(&self, now: u64) -> [u8; 8] {
        match self.standby {
            2 => [0; 8],
            1 => self.frame.map(|r| r.rotate_left(((now / 600) % 8) as u32)),
            _ => match self.animation {
                Some(a) if a.count > 0 && a.count <= 8 && a.period_ms > 0 => {
                    a.frames[((now / u64::from(a.period_ms)) % u64::from(a.count)) as usize]
                }
                _ => self.frame,
            },
        }
    }
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            brightness: 4,
            animation: None,
            frame: [0x0e, 0x0a, 0x0a, 0x0a, 0x3a, 0x7a, 0x73, 0x03],
            standby: 0,
        }
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Action {
    Previous,
    Next,
    PlayPause,
    VolumeUp,
    VolumeDown,
    Mute,
    VoiceAssistant,
    FastForward,
    Rewind,
}
impl Action {
    // Diagnostic: keep Voice Command asserted long enough to distinguish a
    // short-pulse issue from host support for a consumer-only HID device.
    pub fn hold_ms(self) -> u64 {
        if self == Self::VoiceAssistant {
            800
        } else {
            20
        }
    }
    pub fn report(self) -> [u8; 2] {
        let usage: u16 = match self {
            Self::Previous => 0xb6,
            Self::Next => 0xb5,
            Self::PlayPause => 0xcd,
            Self::VolumeUp => 0xe9,
            Self::VolumeDown => 0xea,
            Self::Mute => 0xe2,
            Self::VoiceAssistant => 0xcf,
            Self::FastForward => 0xb3,
            Self::Rewind => 0xb4,
        };
        usage.to_le_bytes()
    }
}
#[derive(Default)]
pub struct State {
    pub settings: Settings,
    visualizer: Option<([u8; 8], u64)>,
    volume: Option<volume::Percent>,
    action: Option<(Action, u64)>,
    pulse_started_at: u64,
    volume_sweep: Option<(Action, u64, u64)>,
}
impl State {
    pub fn set_volume(&mut self, value: Option<volume::Percent>) {
        self.volume = value;
    }
    pub fn clear_transient(&mut self) {
        let settings = self.settings;
        *self = Self::default();
        self.settings = settings;
    }

    pub fn apply(&mut self, c: Command, now: u64) {
        match c {
            Command::Brightness(v) => self.settings.brightness = v,
            Command::Frame(v) => {
                self.settings.frame = v;
                self.settings.animation = None;
            }
            Command::Standby(v) => {
                self.settings.standby = v;
                self.settings.animation = None;
            }
            Command::Visualizer(v) => self.visualizer = Some((v, now)),
        }
    }
    pub fn action(&mut self, a: Action, now: u64) {
        if matches!(a, Action::VolumeUp | Action::VolumeDown) {
            let start = match self.volume_sweep {
                Some((previous, start, last))
                    if previous == a && now.saturating_sub(last) < 180 =>
                {
                    start
                }
                _ => now,
            };
            self.volume_sweep = Some((a, start, now));
        }
        let continuing = matches!(a, Action::VolumeUp | Action::VolumeDown)
            && self
                .action
                .is_some_and(|(previous, at)| previous == a && now.saturating_sub(at) < 500);
        if !continuing {
            self.pulse_started_at = now;
        }
        self.action = Some((a, now));
    }
    pub fn led_mask(&self, now: u64) -> u8 {
        if self.volume.is_some() {
            return (1u8 << self.leds(now)) - 1;
        }
        if let Some((direction, start, last)) = self.volume_sweep {
            if now.saturating_sub(last) < 180 {
                let phase = (now.saturating_sub(start) / 60) % 3;
                let index = if direction == Action::VolumeUp {
                    phase
                } else {
                    2 - phase
                };
                return 1 << index;
            }
        }
        (1u8 << self.leds(now)) - 1
    }
    pub fn leds(&self, _now: u64) -> u8 {
        match self.volume {
            Some(v) => v.get().div_ceil(33).min(3),
            _ => 0,
        }
    }
    pub fn frame(&self, now: u64) -> [u8; 8] {
        if let Some((a, t)) = self.action {
            let age = now.saturating_sub(t);
            if age < 500 {
                return match a {
                    Action::Next | Action::Previous => {
                        // Sample 01: hold briefly, then accelerate off-screen without wrapping.
                        let x = match age {
                            0..=149 => 2,
                            150..=229 => 3,
                            230..=289 => 4,
                            290..=349 => 5,
                            350..=409 => 6,
                            _ => 7,
                        };
                        let mut rows = [0u8; 8];
                        for (y, offset) in [0, 1, 2, 2, 1, 0].into_iter().enumerate() {
                            if x + offset < 8 {
                                rows[y + 1] = 0x80 >> (x + offset);
                            }
                        }
                        if a == Action::Previous {
                            rows.map(u8::reverse_bits)
                        } else {
                            rows
                        }
                    }
                    Action::Mute => [0, 0x42, 0x24, 0x18, 0x18, 0x24, 0x42, 0],
                    Action::VoiceAssistant => [0x18, 0x3c, 0x3c, 0x3c, 0x5a, 0x24, 0x18, 0x3c],
                    Action::FastForward => [0, 0x48, 0x6c, 0x7e, 0x7e, 0x6c, 0x48, 0],
                    Action::Rewind => [0, 0x12, 0x36, 0x7e, 0x7e, 0x36, 0x12, 0],
                    Action::PlayPause => [0, 0x18, 0x3c, 0x7e, 0x7e, 0x3c, 0x18, 0],
                    Action::VolumeUp | Action::VolumeDown => {
                        // Keep the pulse moving during repeated detents; expiry still
                        // follows the last input. Logical pixels are rotated at output.
                        let phase = (now.saturating_sub(self.pulse_started_at) % 500) * 3 / 500;
                        let inset = if a == Action::VolumeDown {
                            1 + phase as usize
                        } else {
                            3 - phase as usize
                        };
                        let mut rows = [0; 8];
                        for n in inset..8 - inset {
                            rows[3] |= 0x80 >> n;
                            rows[4] |= 0x80 >> n;
                            if a == Action::VolumeUp {
                                rows[n] |= 0x18;
                            }
                        }
                        rows
                    }
                };
            }
        }
        if let Some((heights, t)) = self.visualizer {
            if now.saturating_sub(t) < 2000 {
                let mut rows = [0; 8];
                for (x, h) in heights.iter().enumerate() {
                    for y in 0..*h {
                        rows[7 - y as usize] |= 0x80 >> x;
                    }
                }
                return rows;
            }
        }
        self.settings.idle_frame(now)
    }
}
#[derive(Default)]
pub struct Button {
    candidate: bool,
    stable: bool,
    since: u64,
}
impl Button {
    pub fn update(&mut self, pressed: bool, now: u64) -> bool {
        if pressed != self.candidate {
            self.candidate = pressed;
            self.since = now;
        }
        if self.stable != pressed && now.saturating_sub(self.since) >= 20 {
            self.stable = pressed;
            return pressed;
        }
        false
    }
}
pub struct Encoder {
    previous: u8,
    quarters: i8,
}
impl Encoder {
    pub fn new(initial: u8) -> Self {
        Self {
            previous: initial & 3,
            quarters: 0,
        }
    }
    pub fn update(&mut self, new: u8) -> i8 {
        let new = new & 3;
        let old = self.previous;
        self.previous = new;
        if old ^ new == 3 {
            self.quarters = 0;
            return 0;
        }
        self.quarters += match (old, new) {
            (0, 1) | (1, 3) | (3, 2) | (2, 0) => 1,
            (1, 0) | (3, 1) | (2, 3) | (0, 2) => -1,
            _ => 0,
        };
        if self.quarters.abs() == 4 {
            let direction = self.quarters.signum();
            self.quarters = 0;
            direction
        } else {
            0
        }
    }
}
#[derive(Default)]
pub struct Hid {
    release_at: Option<u64>,
    next_press_at: u64,
    pending: std::collections::VecDeque<Action>,
}
impl Hid {
    pub fn retry_release(&mut self, now: u64) {
        self.release_at = Some(now + 5);
    }

    pub fn input(&mut self, a: Action, ready: bool, now: u64) -> Option<[u8; 2]> {
        if !ready {
            *self = Self::default();
            return None;
        }
        if self.release_at.is_some() || now < self.next_press_at || !self.pending.is_empty() {
            if self.pending.len() < 16 {
                self.pending.push_back(a);
            }
            return None;
        }
        self.release_at = Some(now + a.hold_ms());
        Some(a.report())
    }
    pub fn tick(&mut self, ready: bool, now: u64) -> Option<[u8; 2]> {
        if !ready {
            *self = Self::default();
            return None;
        }
        if self.release_at.is_some_and(|t| now >= t) {
            self.release_at = None;
            self.next_press_at = now + 5;
            return Some([0, 0]);
        }
        if self.release_at.is_none() && now >= self.next_press_at {
            if let Some(a) = self.pending.pop_front() {
                self.release_at = Some(now + a.hold_ms());
                return Some(a.report());
            }
        }
        None
    }
}
#[cfg(test)]
mod burst_tests {
    use super::*;
    #[test]
    fn rapid_input_keeps_release_between_presses() {
        let mut h = Hid::default();
        assert_eq!(h.input(Action::Next, true, 0), Some([0xb5, 0]));
        assert_eq!(h.input(Action::VolumeUp, true, 1), None);
        assert_eq!(h.tick(true, 20), Some([0, 0]));
        assert_eq!(h.tick(true, 25), Some([0xe9, 0]));
        assert_eq!(h.tick(true, 45), Some([0, 0]));
    }
    #[test]
    fn queued_input_does_not_survive_disconnect() {
        let mut h = Hid::default();
        h.input(Action::Next, true, 0);
        h.input(Action::Next, true, 1);
        h.tick(false, 2);
        assert_eq!(h.tick(true, 100), None);
    }
}
#[cfg(test)]
mod retry_tests {
    use super::*;
    #[test]
    fn failed_release_is_retried_before_next_press() {
        let mut h = Hid::default();
        h.input(Action::Next, true, 0);
        h.input(Action::Previous, true, 1);
        assert_eq!(h.tick(true, 20), Some([0, 0]));
        h.retry_release(20);
        assert_eq!(h.tick(true, 25), Some([0, 0]));
        assert_eq!(h.tick(true, 30), Some([0xb6, 0]));
    }
}
#[cfg(test)]
mod settings_tests {
    use super::*;
    #[test]
    fn settings_round_trip_and_invalid_storage() {
        let s = Settings {
            brightness: 15,
            standby: 2,
            frame: [1, 2, 3, 4, 5, 6, 7, 8],
            animation: None,
        };
        assert_eq!(Settings::decode(&s.encode()), Some(s));
        assert_eq!(Settings::decode(&[0; 9]), None);
        assert_eq!(Settings::decode(&[16; 10]), None);
    }
    #[test]
    fn standby_off_animation_and_latest_action() {
        let mut s = State::default();
        s.apply(Command::Standby(2), 0);
        assert_eq!(s.frame(0), [0; 8]);
        s.apply(Command::Standby(1), 0);
        assert_ne!(s.frame(0), s.frame(600));
        s.action(Action::Next, 0);
        s.action(Action::PlayPause, 400);
        assert_eq!(s.frame(899), [0, 0x18, 0x3c, 0x7e, 0x7e, 0x3c, 0x18, 0]);
        assert_ne!(s.frame(900), s.frame(899));
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum KnobEvent {
    Click,
    DoubleClick,
    TripleClick,
    Pair,
}
#[derive(Default)]
pub struct Knob {
    button: Button,
    pressed_at: Option<u64>,
    long_sent: bool,
    clicks: u8,
    released_at: Option<u64>,
}
impl Knob {
    pub const MULTI_CLICK_MS: u64 = 300;
    pub fn cancel_clicks(&mut self) {
        self.clicks = 0;
        self.released_at = None;
        // A press spanning a connection transition must not become a click.
        self.long_sent = true;
    }
    pub fn update(&mut self, pressed: bool, now: u64) -> Option<KnobEvent> {
        if !self.button.stable
            && !self.button.candidate
            && self
                .released_at
                .is_some_and(|t| now.saturating_sub(t) >= Self::MULTI_CLICK_MS)
        {
            let count = self.clicks;
            self.clicks = 0;
            self.released_at = None;
            self.button.update(pressed, now);
            return match count {
                1 => Some(KnobEvent::Click),
                2 => Some(KnobEvent::DoubleClick),
                3 => Some(KnobEvent::TripleClick),
                _ => None, // Four or more clicks are not a defined gesture.
            };
        }
        let was_pressed = self.button.stable;
        self.button.update(pressed, now);
        if !was_pressed && self.button.stable {
            self.pressed_at = Some(now);
            self.long_sent = false;
        }
        if was_pressed && !self.button.stable {
            self.pressed_at = None;
            if !self.long_sent {
                self.clicks = self.clicks.saturating_add(1);
                self.released_at = Some(now);
            }
        }
        if pressed
            && self.button.stable
            && !self.long_sent
            && self
                .pressed_at
                .is_some_and(|t| now.saturating_sub(t) >= 3000)
        {
            self.cancel_clicks();
            return Some(KnobEvent::Pair);
        }
        None
    }
}
#[cfg(test)]
mod knob_tests {
    use super::*;
    fn tap(k: &mut Knob, t: u64) {
        for (down, at) in [(true, t), (true, t + 20), (false, t + 60), (false, t + 80)] {
            assert_eq!(k.update(down, at), None);
        }
    }
    #[test]
    fn multi_clicks_emit_only_one_action() {
        for (count, expected) in [
            (1, Some(KnobEvent::Click)),
            (2, Some(KnobEvent::DoubleClick)),
            (3, Some(KnobEvent::TripleClick)),
            (4, None),
        ] {
            let mut k = Knob::default();
            for i in 0..count {
                tap(&mut k, i * 150);
            }
            let released = (count - 1) * 150 + 80;
            assert_eq!(k.update(false, released + 299), None);
            assert_eq!(k.update(false, released + 300), expected);
            assert_eq!(k.update(false, released + 600), None);
        }
    }
    #[test]
    fn hold_cancels_pending_clicks_and_disconnect_drops_them() {
        let mut k = Knob::default();
        tap(&mut k, 0);
        k.update(true, 150);
        k.update(true, 170);
        assert_eq!(k.update(true, 500), None);
        assert_eq!(k.update(true, 3170), Some(KnobEvent::Pair));
        k.update(false, 3200);
        k.update(false, 3220);
        assert_eq!(k.update(false, 3600), None);
        tap(&mut k, 4000);
        k.cancel_clicks();
        assert_eq!(k.update(false, 4500), None);
    }
    #[test]
    fn press_after_deadline_starts_new_sequence() {
        let mut k = Knob::default();
        tap(&mut k, 0);
        assert_eq!(k.update(true, 400), Some(KnobEvent::Click));
        k.update(true, 420);
        k.update(false, 460);
        k.update(false, 480);
        assert_eq!(k.update(false, 780), Some(KnobEvent::Click));
    }
    #[test]
    fn short_press_only_on_debounced_release() {
        let mut k = Knob::default();
        assert_eq!(k.update(true, 0), None);
        assert_eq!(k.update(true, 20), None);
        assert_eq!(k.update(false, 100), None);
        assert_eq!(k.update(false, 120), None);
        assert_eq!(k.update(false, 420), Some(KnobEvent::Click));
        assert_eq!(k.update(false, 500), None);
    }
    #[test]
    fn long_press_once_and_never_play_on_release() {
        let mut k = Knob::default();
        k.update(true, 0);
        k.update(true, 20);
        assert_eq!(k.update(true, 3019), None);
        assert_eq!(k.update(true, 3020), Some(KnobEvent::Pair));
        assert_eq!(k.update(true, 5000), None);
        k.update(false, 5010);
        assert_eq!(k.update(false, 5030), None);
    }
    #[test]
    fn bounce_does_not_click() {
        let mut k = Knob::default();
        k.update(true, 0);
        k.update(false, 10);
        assert_eq!(k.update(false, 30), None);
    }
}

#[derive(Default)]
pub struct PairingWindow {
    until: u64,
}
impl PairingWindow {
    pub fn open(&mut self, now: u64) {
        self.until = now.saturating_add(60_000);
    }
    pub fn active(&self, now: u64) -> bool {
        now < self.until
    }
    pub fn allows(&self, known: bool, now: u64) -> bool {
        // An explicit pairing window must also permit repairing a bond that
        // only the peripheral remembers (the phone may have forgotten it).
        known || self.active(now)
    }
    pub fn close(&mut self) {
        self.until = 0;
    }
}
#[cfg(test)]
mod pairing_tests {
    use super::*;
    #[test]
    fn new_phone_requires_window_and_timeout_closes_it() {
        let mut p = PairingWindow::default();
        assert!(!p.allows(false, 0));
        assert!(p.allows(true, 0));
        p.open(100);
        assert!(p.allows(false, 60099));
        assert!(p.allows(true, 60099));
        assert!(!p.allows(false, 60100));
        assert!(p.allows(true, 60100));
        p.open(70000);
        p.close();
        assert!(!p.allows(false, 70001));
    }
}

/// Convert logical rows (top to bottom, MSB at left) to the stand's mounted matrix.
pub fn mounted_frame(rows: [u8; 8]) -> [u8; 8] {
    // The module is mounted 90 degrees counterclockwise when viewed from front.
    // Rotate pixels clockwise: (x, y) -> (7 - y, x).
    let mut rotated = [0; 8];
    for (y, row) in rows.iter().enumerate() {
        for (x, output_row) in rotated.iter_mut().enumerate() {
            if row & (0x80 >> x) != 0 {
                *output_row |= 1 << y;
            }
        }
    }
    rotated
}

#[cfg(test)]
mod mounting_tests {
    use super::mounted_frame;

    #[test]
    fn counterclockwise_mount_requires_clockwise_pixels() {
        // An asymmetric L at top-left becomes a reversed L at top-right.
        assert_eq!(
            mounted_frame([0x80, 0x80, 0xe0, 0, 0, 0, 0, 0]),
            [0x07, 0x04, 0x04, 0, 0, 0, 0, 0]
        );
        // Bottom-left -> top-left; top-right -> bottom-right.
        assert_eq!(
            mounted_frame([0x01, 0, 0, 0, 0, 0, 0, 0x80]),
            [0x80, 0, 0, 0, 0, 0, 0, 0x01]
        );
    }
}

#[cfg(test)]
mod volume_pulse_tests {
    use super::*;

    #[test]
    fn volume_up_expands_down_shrinks_then_returns_to_visualizer() {
        for (action, first, middle, last) in [
            (
                Action::VolumeUp,
                [0, 0, 0, 0x18, 0x18, 0, 0, 0],
                [0, 0, 0x18, 0x3c, 0x3c, 0x18, 0, 0],
                [0, 0x18, 0x18, 0x7e, 0x7e, 0x18, 0x18, 0],
            ),
            (
                Action::VolumeDown,
                [0, 0, 0, 0x7e, 0x7e, 0, 0, 0],
                [0, 0, 0, 0x3c, 0x3c, 0, 0, 0],
                [0, 0, 0, 0x18, 0x18, 0, 0, 0],
            ),
        ] {
            let mut state = State::default();
            state.apply(Command::Visualizer([8; 8]), 0);
            state.action(action, 0);
            assert_eq!(state.frame(0), first);
            assert_eq!(state.frame(167), middle);
            assert_eq!(state.frame(334), last);
            assert_eq!(state.frame(499), last);
            assert_eq!(state.frame(500), [255; 8]);
        }
    }

    #[test]
    fn rapid_rotation_keeps_pulse_moving_and_direction_change_restarts_it() {
        let mut state = State::default();
        state.action(Action::VolumeUp, 0);
        state.action(Action::VolumeUp, 100);
        assert_eq!(state.frame(170), [0, 0, 0x18, 0x3c, 0x3c, 0x18, 0, 0]);
        assert_ne!(state.frame(599), state.settings.frame);
        assert_eq!(state.frame(600), state.settings.frame);
        state.action(Action::VolumeDown, 610);
        assert_eq!(state.frame(610), [0, 0, 0, 0x7e, 0x7e, 0, 0, 0]);
        state.action(Action::VolumeDown, 710);
        assert_eq!(state.frame(780), [0, 0, 0, 0x3c, 0x3c, 0, 0, 0]);
        state.action(Action::VolumeUp, 800);
        assert_eq!(state.frame(800), [0, 0, 0, 0x18, 0x18, 0, 0, 0]);
    }
}

mod setup_buttons;
pub use setup_buttons::{SetupButtons, SetupEvent};
pub mod captive_dns;

#[cfg(test)]
mod animation_tests {
    use super::*;
    fn animated() -> Settings {
        let mut s = Settings::default();
        let mut frames = [[0; 8]; 8];
        frames[0] = [1; 8];
        frames[1] = [2; 8];
        s.frame = frames[0];
        s.animation = Some(Animation {
            frames,
            count: 2,
            period_ms: 200,
        });
        s
    }
    #[test]
    fn old_storage_loads_without_animation() {
        let old = [4, 0, 1, 2, 3, 4, 5, 6, 7, 8];
        let s = Settings::decode(&old).unwrap();
        assert!(s.animation.is_none());
        assert_eq!(s.idle_frame(1000), [1, 2, 3, 4, 5, 6, 7, 8]);
    }
    #[test]
    fn animation_roundtrip_and_reject_invalid_storage() {
        let s = animated();
        let b = s.encode();
        assert_eq!(Settings::decode(&b), Some(s));
        for (i, v) in [(10, 2), (11, 9), (12, 0), (0, 16)] {
            let mut bad = b;
            bad[i] = v;
            if i == 12 {
                bad[13] = 0;
            }
            assert!(Settings::decode(&bad).is_none());
        }
        for len in [0, 9, 11, 77] {
            assert!(Settings::decode(&b[..len]).is_none());
        }
    }
    #[test]
    fn eight_frames_boundaries_and_legacy_modes() {
        let mut s = animated();
        let a = s.animation.as_mut().unwrap();
        a.count = 8;
        a.period_ms = 100;
        a.frames[7] = [128; 8];
        assert_eq!(Settings::decode(&s.encode()), Some(s));
        assert_eq!(s.idle_frame(700), [128; 8]);
        assert_eq!(s.idle_frame(800), [1; 8]);
        s.animation.as_mut().unwrap().period_ms = 2000;
        assert_eq!(Settings::decode(&s.encode()), Some(s));
        s.animation.as_mut().unwrap().period_ms = 2001;
        assert!(Settings::decode(&s.encode()).is_none());
        s.standby = 2;
        assert_eq!(s.idle_frame(700), [0; 8]);
        s.standby = 1;
        assert_eq!(s.idle_frame(600), [2; 8]);
    }
    #[test]
    fn animation_loops_and_android_overrides_only_display() {
        let mut s = State::default();
        s.settings = animated();
        assert_eq!(s.frame(0), [1; 8]);
        assert_eq!(s.frame(199), [1; 8]);
        assert_eq!(s.frame(200), [2; 8]);
        assert_eq!(s.frame(400), [1; 8]);
        s.apply(Command::Brightness(9), 0);
        assert!(s.settings.animation.is_some());
        s.action(Action::PlayPause, 0);
        assert_ne!(s.frame(200), [2; 8]);
        assert_eq!(s.frame(600), [2; 8]);
        s.apply(Command::Frame([3; 8]), 700);
        assert!(s.settings.animation.is_none());
        assert_eq!(s.frame(1000), [3; 8]);
        s.settings = animated();
        s.apply(Command::Standby(0), 1000);
        assert!(s.settings.animation.is_none());
    }
}

#[cfg(test)]
mod track_animation_tests {
    use super::*;
    #[test]
    fn chevron_moves_without_wrapping_and_previous_is_mirrored() {
        let mut next = State::default();
        let mut previous = State::default();
        next.action(Action::Next, 1000);
        previous.action(Action::Previous, 1000);
        assert_eq!(next.frame(1000), [0, 32, 16, 8, 8, 16, 32, 0]);
        assert_eq!(next.frame(1149), next.frame(1000));
        assert_eq!(next.frame(1150), [0, 16, 8, 4, 4, 8, 16, 0]);
        assert_eq!(next.frame(1410), [0, 1, 0, 0, 0, 0, 1, 0]);
        for t in 1000..1500 {
            assert_eq!(previous.frame(t), next.frame(t).map(u8::reverse_bits));
        }
        assert_eq!(next.frame(1500), next.settings.idle_frame(1500));
    }
    #[test]
    fn repeated_track_press_restarts_and_extends_animation() {
        let mut s = State::default();
        s.action(Action::Next, 0);
        let first = s.frame(0);
        s.action(Action::Next, 280);
        assert_eq!(s.frame(280), first);
        assert_eq!(s.frame(779), [0, 1, 0, 0, 0, 0, 1, 0]);
        assert_eq!(s.frame(780), s.settings.idle_frame(780));
        s.action(Action::Previous, 800);
        assert_eq!(s.frame(800), first.map(u8::reverse_bits));
    }
}

#[cfg(test)]
mod volume_led_animation_tests {
    use super::*;
    #[test]
    fn known_volume_takes_priority_over_sweep_including_zero() {
        let mut s = State::default();
        s.action(Action::VolumeUp, 100);
        assert_eq!(s.led_mask(100), 1);
        s.set_volume(volume::Percent::new(100));
        assert_eq!(s.led_mask(160), 7);
        s.set_volume(volume::Percent::new(50));
        assert_eq!(s.led_mask(220), 3);
        s.action(Action::VolumeDown, 400);
        assert_eq!(s.led_mask(400), 3);
        assert_eq!(s.led_mask(460), 3);
        s.set_volume(volume::Percent::new(0));
        assert_eq!(s.led_mask(520), 0);
        s.set_volume(None);
        s.action(Action::VolumeDown, 700);
        assert_eq!(s.led_mask(700), 4);
        assert_eq!(s.led_mask(880), 0);
    }
    #[test]
    fn repeated_turns_continue_reverse_restarts_and_unknown_returns_off() {
        let mut s = State::default();
        s.action(Action::VolumeUp, 0);
        s.action(Action::VolumeUp, 80);
        assert_eq!(s.led_mask(80), 2);
        s.action(Action::VolumeUp, 150);
        assert_eq!(s.led_mask(180), 1);
        s.action(Action::VolumeDown, 200);
        assert_eq!(s.led_mask(200), 4);
        assert_eq!(s.led_mask(260), 2);
        assert_eq!(s.led_mask(380), 0);
        s.action(Action::VolumeDown, 400);
        assert_eq!(s.led_mask(400), 4);
    }
}
