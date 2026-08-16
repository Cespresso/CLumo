#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CountdownState {
    Idle,
    Running,
    Paused,
    Completed,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DurationSetting {
    pub minutes: u8,
    pub seconds: u8,
}

pub const DEFAULT_DURATION: DurationSetting = DurationSetting {
    minutes: 5,
    seconds: 0,
};

pub fn duration_is_valid(minutes: u8, seconds: u8) -> bool {
    minutes < 60 && seconds < 60 && (minutes != 0 || seconds != 0)
}

impl DurationSetting {
    pub fn new(minutes: u8, seconds: u8) -> Option<Self> {
        duration_is_valid(minutes, seconds).then_some(Self { minutes, seconds })
    }

    pub fn total_secs(self) -> u16 {
        self.minutes as u16 * 60 + self.seconds as u16
    }
}

pub fn encode_persisted_duration(setting: DurationSetting) -> u16 {
    setting.total_secs()
}

pub fn decode_persisted_duration(total_secs: u16) -> DurationSetting {
    if total_secs == 0 || total_secs > 59 * 60 + 59 {
        return DEFAULT_DURATION;
    }
    DurationSetting {
        minutes: (total_secs / 60) as u8,
        seconds: (total_secs % 60) as u8,
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Countdown {
    state: CountdownState,
    setting: DurationSetting,
    remaining_ms: u64,
}

impl Default for Countdown {
    fn default() -> Self {
        Self::new(DEFAULT_DURATION)
    }
}

impl Countdown {
    pub fn new(setting: DurationSetting) -> Self {
        Self {
            state: CountdownState::Idle,
            setting,
            remaining_ms: setting.total_secs() as u64 * 1_000,
        }
    }

    pub fn state(&self) -> CountdownState {
        self.state
    }

    pub fn setting(&self) -> DurationSetting {
        self.setting
    }

    pub fn remaining_secs(&self) -> u16 {
        self.remaining_ms.div_ceil(1_000) as u16
    }

    /// Starts or resumes the countdown and reports whether the state changed.
    pub fn start_resume(&mut self) -> bool {
        match self.state {
            CountdownState::Idle | CountdownState::Completed => {
                self.remaining_ms = self.setting.total_secs() as u64 * 1_000;
                self.state = CountdownState::Running;
                true
            }
            CountdownState::Paused => {
                self.state = CountdownState::Running;
                true
            }
            CountdownState::Running => false,
        }
    }

    pub fn pause(&mut self) {
        if self.state == CountdownState::Running {
            self.state = CountdownState::Paused;
        }
    }

    pub fn cancel(&mut self) {
        self.state = CountdownState::Idle;
        self.remaining_ms = self.setting.total_secs() as u64 * 1_000;
    }

    pub fn set_duration(&mut self, setting: DurationSetting) -> bool {
        if self.state != CountdownState::Idle {
            return false;
        }
        self.setting = setting;
        self.remaining_ms = setting.total_secs() as u64 * 1_000;
        true
    }

    pub fn tick(&mut self, elapsed_ms: u64) -> bool {
        if self.state != CountdownState::Running {
            return false;
        }
        let previous_secs = self.remaining_secs();
        self.remaining_ms = self.remaining_ms.saturating_sub(elapsed_ms);
        if self.remaining_ms == 0 {
            self.state = CountdownState::Completed;
        }
        self.remaining_secs() != previous_secs || self.state == CountdownState::Completed
    }
}

pub fn progress_pixels(remaining_secs: u16, total_secs: u16) -> u8 {
    if total_secs == 0 {
        return 0;
    }
    let lit = (remaining_secs as u32 * 64).div_ceil(total_secs as u32);
    lit.min(64) as u8
}

/// Generate an 8x8 frame whose remaining lit pixels occupy the row-major
/// suffix, so pixels turn off from the top-left toward the bottom-right.
pub fn progress_frame(lit: u8) -> [u8; 8] {
    let lit = lit.min(64);
    let mut frame = [0u8; 8];
    let full_rows = (lit / 8) as usize;
    let remaining = lit % 8;

    for row in frame.iter_mut().rev().take(full_rows) {
        *row = 0xFF;
    }
    if full_rows < 8 && remaining > 0 {
        frame[7 - full_rows] = (1 << remaining) - 1;
    }
    frame
}

pub fn encode_timer_status(timer: &Countdown) -> [u8; 5] {
    let state = match timer.state() {
        CountdownState::Idle => 0,
        CountdownState::Running => 1,
        CountdownState::Paused => 2,
        CountdownState::Completed => 3,
    };
    let remaining = timer.remaining_secs();
    let setting = timer.setting();
    [
        state,
        (remaining >> 8) as u8,
        remaining as u8,
        setting.minutes,
        setting.seconds,
    ]
}

pub fn parse_timer_duration(data: &[u8]) -> Option<DurationSetting> {
    if data.len() != 3 || data[0] != 0x10 {
        return None;
    }
    DurationSetting::new(data[1], data[2])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_duration_range() {
        assert_eq!(
            DurationSetting::new(5, 0),
            Some(DurationSetting {
                minutes: 5,
                seconds: 0,
            })
        );
        assert_eq!(DurationSetting::new(0, 0), None);
        assert_eq!(DurationSetting::new(60, 0), None);
        assert_eq!(DurationSetting::new(0, 60), None);
    }

    #[test]
    fn counts_down_pauses_resumes_and_completes() {
        let mut timer = Countdown::default();
        assert_eq!(timer.state(), CountdownState::Idle);
        assert_eq!(timer.remaining_secs(), 300);

        timer.start_resume();
        timer.tick(1_001);
        assert_eq!(timer.remaining_secs(), 299);

        timer.pause();
        timer.tick(5_000);
        assert_eq!(timer.remaining_secs(), 299);

        timer.start_resume();
        timer.tick(299_000);
        assert_eq!(timer.state(), CountdownState::Completed);
        assert_eq!(timer.remaining_secs(), 0);
    }

    #[test]
    fn cancel_restores_configured_duration() {
        let mut timer = Countdown::default();
        timer.start_resume();
        timer.tick(30_000);
        timer.cancel();

        assert_eq!(timer.state(), CountdownState::Idle);
        assert_eq!(timer.remaining_secs(), 300);
    }

    #[test]
    fn completed_timer_can_restart() {
        let mut timer = Countdown::default();
        timer.start_resume();
        timer.tick(300_000);
        assert_eq!(timer.state(), CountdownState::Completed);

        timer.start_resume();
        assert_eq!(timer.state(), CountdownState::Running);
        assert_eq!(timer.remaining_secs(), 300);
    }

    #[test]
    fn duplicate_start_is_a_true_no_op() {
        let mut timer = Countdown::default();
        assert!(timer.start_resume());
        timer.tick(1_500);
        let remaining = timer.remaining_secs();

        assert!(!timer.start_resume());
        assert_eq!(timer.remaining_secs(), remaining);
    }

    #[test]
    fn cancel_restores_duration_from_paused_and_completed() {
        let mut timer = Countdown::default();
        timer.start_resume();
        timer.tick(1_500);
        timer.pause();
        timer.cancel();
        assert_eq!(timer.state(), CountdownState::Idle);
        assert_eq!(timer.remaining_secs(), 300);

        timer.start_resume();
        timer.tick(300_000);
        timer.cancel();
        assert_eq!(timer.state(), CountdownState::Idle);
        assert_eq!(timer.remaining_secs(), 300);
    }

    #[test]
    fn duration_can_change_only_while_idle() {
        let mut timer = Countdown::default();
        let setting = DurationSetting::new(1, 30).unwrap();
        assert!(timer.set_duration(setting));
        assert_eq!(timer.remaining_secs(), 90);

        timer.start_resume();
        assert!(!timer.set_duration(DurationSetting::new(2, 0).unwrap()));
        assert_eq!(timer.setting(), setting);
    }

    #[test]
    fn calculates_progress_pixels_with_ceiling_division() {
        assert_eq!(progress_pixels(300, 300), 64);
        assert_eq!(progress_pixels(299, 300), 64);
        assert_eq!(progress_pixels(150, 300), 32);
        assert_eq!(progress_pixels(1, 300), 1);
        assert_eq!(progress_pixels(0, 300), 0);
        assert_eq!(progress_pixels(1, 0), 0);
    }

    #[test]
    fn progress_frame_turns_off_pixels_from_top_left_to_bottom_right() {
        assert_eq!(progress_frame(64), [0xFF; 8]);
        assert_eq!(
            progress_frame(32),
            [0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF]
        );
        assert_eq!(
            progress_frame(9),
            [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0xFF]
        );
        assert_eq!(
            progress_frame(1),
            [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01]
        );
        assert_eq!(progress_frame(0), [0x00; 8]);
    }

    #[test]
    fn encodes_five_byte_timer_status() {
        assert_eq!(
            encode_timer_status(&Countdown::default()),
            [0, 0x01, 0x2c, 5, 0]
        );
    }

    #[test]
    fn parses_only_valid_set_duration_commands() {
        assert_eq!(
            parse_timer_duration(&[0x10, 59, 59]),
            Some(DurationSetting {
                minutes: 59,
                seconds: 59,
            })
        );
        assert_eq!(parse_timer_duration(&[0x10, 0, 0]), None);
        assert_eq!(parse_timer_duration(&[0x10, 60, 0]), None);
        assert_eq!(parse_timer_duration(&[0x10, 0, 60]), None);
        assert_eq!(parse_timer_duration(&[0x10, 1]), None);
        assert_eq!(parse_timer_duration(&[0x10, 1, 0, 0xFF]), None);
        assert_eq!(parse_timer_duration(&[0x01, 1, 0]), None);
    }

    #[test]
    fn timer_setting_round_trips_through_persisted_total_seconds() {
        let setting = DurationSetting::new(59, 59).unwrap();

        assert_eq!(
            decode_persisted_duration(encode_persisted_duration(setting)),
            setting
        );
        assert_eq!(decode_persisted_duration(0), DEFAULT_DURATION);
        assert_eq!(decode_persisted_duration(3_600), DEFAULT_DURATION);
    }
}
