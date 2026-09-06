// The MODE values persisted in NVS.
pub const MODE_PET: u8 = 0;
pub const MODE_POMODORO: u8 = 1;
pub const MODE_DICE: u8 = 2;

pub const MODE_COUNT: u8 = 3;

/// What a device with nothing usable in NVS boots into.
pub const MODE_DEFAULT: u8 = MODE_POMODORO;

pub fn decode_mode(value: u8) -> u8 {
    if value < MODE_COUNT {
        value
    } else {
        MODE_DEFAULT
    }
}

pub fn next_mode(value: u8) -> u8 {
    (decode_mode(value) + 1) % MODE_COUNT
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stored_values_in_range_are_taken_as_they_are() {
        assert_eq!(decode_mode(MODE_PET), MODE_PET);
        assert_eq!(decode_mode(MODE_POMODORO), MODE_POMODORO);
        assert_eq!(decode_mode(MODE_DICE), MODE_DICE);
    }

    #[test]
    fn a_value_out_of_range_falls_back_to_the_same_mode_a_fresh_device_uses() {
        assert_eq!(decode_mode(MODE_COUNT), MODE_DEFAULT);
        assert_eq!(decode_mode(u8::MAX), MODE_DEFAULT);
    }

    #[test]
    fn the_cycle_visits_every_mode_and_returns() {
        let mut mode = MODE_PET;
        let mut seen = vec![mode];
        for _ in 0..MODE_COUNT {
            mode = next_mode(mode);
            seen.push(mode);
        }
        assert_eq!(seen, vec![MODE_PET, MODE_POMODORO, MODE_DICE, MODE_PET]);
    }

    #[test]
    fn the_cycle_recovers_from_a_value_out_of_range() {
        assert_eq!(next_mode(u8::MAX), MODE_DICE);
    }
}
