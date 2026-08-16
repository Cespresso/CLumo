pub const DEFAULT_BRIGHTNESS: u8 = 15;

pub fn decode_brightness(stored: Option<u8>) -> u8 {
    stored.unwrap_or(DEFAULT_BRIGHTNESS).min(DEFAULT_BRIGHTNESS)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn brightness_defaults_to_max_and_clamps_persisted_values() {
        assert_eq!(decode_brightness(None), 15);
        assert_eq!(decode_brightness(Some(7)), 7);
        assert_eq!(decode_brightness(Some(255)), 15);
    }
}
