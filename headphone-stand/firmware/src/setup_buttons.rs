use super::Button;
#[derive(Debug, PartialEq, Eq)]
pub enum SetupEvent {
    Left,
    Right,
    Toggle,
}
#[derive(Default)]
pub struct SetupButtons {
    buttons: [Button; 2],
    armed: bool,
    pending: std::collections::VecDeque<(usize, u64)>,
    chord: bool,
    held_since: Option<u64>,
}
impl SetupButtons {
    pub fn update(&mut self, raw: [bool; 2], now: u64) -> Option<SetupEvent> {
        let edges = [
            self.buttons[0].update(raw[0], now),
            self.buttons[1].update(raw[1], now),
        ];
        let down = [self.buttons[0].stable, self.buttons[1].stable];
        if !self.armed {
            if raw == [false; 2] && down == [false; 2] {
                self.armed = true;
            }
            return None;
        }
        if self.chord {
            if down != [true; 2] {
                self.held_since = None;
            }
            if raw == [false; 2] && down == [false; 2] {
                self.chord = false;
            }
            if raw == [true; 2]
                && self
                    .held_since
                    .is_some_and(|t| now.saturating_sub(t) >= 3000)
            {
                self.held_since = None;
                return Some(SetupEvent::Toggle);
            }
            return None;
        }
        if down == [true; 2] {
            self.pending.clear();
            self.chord = true;
            self.held_since = Some(now);
            return None;
        }
        for (i, edge) in edges.into_iter().enumerate() {
            if edge {
                self.pending.push_back((i, now));
            }
        }
        // Wait for the other button's debounce before deciding a single press.
        if raw == [true; 2] {
            return None;
        }
        if self
            .pending
            .front()
            .is_some_and(|(_, t)| now.saturating_sub(*t) >= 150)
        {
            let (i, _) = self.pending.pop_front().unwrap();
            return Some(if i == 0 {
                SetupEvent::Left
            } else {
                SetupEvent::Right
            });
        }
        None
    }
    pub fn progress(&self, now: u64) -> Option<u8> {
        self.held_since
            .map(|t| ((now.saturating_sub(t) * 8 / 3000).min(8)) as u8)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn armed() -> SetupButtons {
        let mut b = SetupButtons::default();
        b.update([false; 2], 0);
        b.update([false; 2], 20);
        b
    }
    #[test]
    fn short_press_is_delayed_but_not_lost() {
        let mut b = armed();
        assert_eq!(b.update([true, false], 100), None);
        assert_eq!(b.update([true, false], 120), None);
        assert_eq!(b.update([false, false], 160), None);
        assert_eq!(b.update([false, false], 180), None);
        assert_eq!(b.update([false, false], 270), Some(SetupEvent::Left));
        assert_eq!(b.update([false, false], 300), None);
    }
    #[test]
    fn chord_suppresses_tracks_and_fires_once() {
        let mut b = armed();
        for (raw, t) in [
            ([true, false], 100),
            ([true, false], 120),
            ([true, true], 180),
            ([true, true], 200),
            ([true, true], 270),
            ([true, true], 3199),
        ] {
            assert_eq!(b.update(raw, t), None);
        }
        assert_eq!(b.update([true, true], 3200), Some(SetupEvent::Toggle));
        assert_eq!(b.update([true, true], 9000), None);
        assert_eq!(b.update([true, false], 9020), None);
        assert_eq!(b.update([true, false], 9040), None);
    }
    #[test]
    fn cancelled_chord_does_not_become_track_or_toggle() {
        let mut b = armed();
        for (raw, t) in [
            ([true, true], 100),
            ([true, true], 120),
            ([false, true], 500),
            ([false, true], 520),
            ([true, true], 600),
            ([true, true], 620),
            ([true, true], 5000),
            ([false, false], 5020),
            ([false, false], 5040),
        ] {
            assert_eq!(b.update(raw, t), None);
        }
        b.update([false, true], 5100);
        b.update([false, true], 5120);
        assert_eq!(b.update([false, true], 5270), Some(SetupEvent::Right));
    }
    #[test]
    fn rapid_single_taps_are_both_delivered() {
        let mut b = armed();
        for (raw, t) in [
            ([true, false], 100),
            ([true, false], 120),
            ([false, false], 150),
            ([false, false], 170),
            ([true, false], 200),
            ([true, false], 220),
        ] {
            assert_eq!(b.update(raw, t), None);
        }
        assert_eq!(b.update([true, false], 270), Some(SetupEvent::Left));
        assert_eq!(b.update([true, false], 370), Some(SetupEvent::Left));
    }
    #[test]
    fn second_button_debounce_at_delay_boundary_does_not_skip() {
        let mut b = armed();
        for (raw, t) in [
            ([true, false], 100),
            ([true, false], 120),
            ([true, true], 269),
            ([true, true], 270),
            ([true, true], 289),
        ] {
            assert_eq!(b.update(raw, t), None);
        }
        assert_eq!(b.update([true, true], 3289), Some(SetupEvent::Toggle));
    }
    #[test]
    fn boot_hold_requires_release() {
        let mut b = SetupButtons::default();
        for t in [0, 20, 3020, 10000] {
            assert_eq!(b.update([true, true], t), None);
        }
        b.update([false, false], 11000);
        b.update([false, false], 11020);
        b.update([true, true], 12000);
        b.update([true, true], 12020);
        assert_eq!(b.update([true, true], 15020), Some(SetupEvent::Toggle));
    }
}
