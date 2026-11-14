//! Physical gestures are independent of media actions. System holds stay reserved.
use crate::Action;

#[derive(Clone, Copy, Debug)]
pub enum Gesture {
    LeftButton,
    RightButton,
    KnobClick,
    KnobDoubleClick,
    KnobTripleClick,
    Clockwise,
    Counterclockwise,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Bindings([Option<Action>; 7]);
impl Default for Bindings {
    fn default() -> Self {
        Self([
            Some(Action::Previous),
            Some(Action::Next),
            Some(Action::PlayPause),
            Some(Action::Next),
            Some(Action::Previous),
            Some(Action::VolumeUp),
            Some(Action::VolumeDown),
        ])
    }
}
impl Bindings {
    pub fn encode(&self) -> [u8; 7] {
        let mut bytes = [1, 0, 0, 0, 0, 0, 0];
        for (i, action) in self.0[..5].iter().enumerate() {
            bytes[i + 1] = match action {
                None => 0,
                Some(Action::PlayPause) => 1,
                Some(Action::Previous) => 2,
                Some(Action::Next) => 3,
                Some(Action::Mute) => 4,
                Some(Action::VoiceAssistant) => 5,
                Some(Action::FastForward) => 6,
                Some(Action::Rewind) => 7,
                _ => 0,
            };
        }
        bytes[6] = u8::from(self.0[5] == Some(Action::VolumeUp));
        bytes
    }
    pub fn decode(bytes: &[u8]) -> Option<Self> {
        if bytes.len() != 7 || bytes[0] != 1 || bytes[6] > 1 {
            return None;
        }
        let mut result = Self::default();
        for (i, code) in bytes[1..6].iter().enumerate() {
            result.0[i] = match code {
                0 => None,
                1 => Some(Action::PlayPause),
                2 => Some(Action::Previous),
                3 => Some(Action::Next),
                4 => Some(Action::Mute),
                5 => Some(Action::VoiceAssistant),
                6 => Some(Action::FastForward),
                7 => Some(Action::Rewind),
                _ => return None,
            };
        }
        if bytes[6] == 0 {
            result.0[5] = None;
            result.0[6] = None;
        }
        Some(result)
    }
    pub fn action(&self, gesture: Gesture) -> Option<Action> {
        self.0[gesture as usize]
    }
    pub fn assign(&mut self, gesture: Gesture, action: Option<Action>) {
        self.0[gesture as usize] = action;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn trial_commands_and_profiles() {
        for (a, usage) in [
            (Action::Mute, 0xe2),
            (Action::VoiceAssistant, 0xcf),
            (Action::FastForward, 0xb3),
            (Action::Rewind, 0xb4),
        ] {
            assert_eq!(a.report(), [usage, 0]);
            let mut hid = crate::Hid::default();
            assert_eq!(hid.input(a, true, 0), Some([usage, 0]));
            assert_eq!(hid.tick(true, a.hold_ms() - 1), None);
            assert_eq!(hid.tick(true, a.hold_ms()), Some([0, 0]));
        }
    }
    #[test]
    fn binding_wire_roundtrip_and_validation() {
        assert_eq!(Bindings::default().encode(), [1, 2, 3, 1, 3, 2, 1]);
        for code in 0..=7 {
            for rotation in 0..=1 {
                let bytes = [1, code, code, code, code, code, rotation];
                assert_eq!(Bindings::decode(&bytes).unwrap().encode(), bytes);
            }
        }
        for bytes in [
            &[1, 2][..],
            &[2, 2, 3, 1, 3, 2, 1],
            &[1, 8, 3, 1, 3, 2, 1],
            &[1, 2, 3, 1, 3, 2, 2],
        ] {
            assert!(Bindings::decode(bytes).is_none());
        }
    }
    #[test]
    fn buttons_and_knob_share_actions_but_assignments_are_independent() {
        let mut b = Bindings::default();
        assert_eq!(
            b.action(Gesture::LeftButton),
            b.action(Gesture::KnobTripleClick)
        );
        assert_eq!(
            b.action(Gesture::RightButton),
            b.action(Gesture::KnobDoubleClick)
        );
        b.assign(Gesture::LeftButton, Some(Action::PlayPause));
        assert_eq!(b.action(Gesture::LeftButton), Some(Action::PlayPause));
        assert_eq!(b.action(Gesture::KnobTripleClick), Some(Action::Previous));
        b.assign(Gesture::RightButton, None);
        assert_eq!(b.action(Gesture::RightButton), None);
    }
}
