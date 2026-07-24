pub const MODE_COUNT: u8 = 4;

pub fn decode_mode(value: u8) -> u8 {
    if value < MODE_COUNT {
        value
    } else {
        0
    }
}

pub fn migrate_legacy_mode(value: u8) -> u8 {
    match value {
        0 => 0,
        1 => 2,
        2 => 3,
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_new_mode_values() {
        assert_eq!(decode_mode(0), 0);
        assert_eq!(decode_mode(1), 1);
        assert_eq!(decode_mode(2), 2);
        assert_eq!(decode_mode(3), 3);
        assert_eq!(decode_mode(99), 0);
    }

    #[test]
    fn migrates_legacy_mode_values() {
        assert_eq!(migrate_legacy_mode(0), 0);
        assert_eq!(migrate_legacy_mode(1), 2);
        assert_eq!(migrate_legacy_mode(2), 3);
        assert_eq!(migrate_legacy_mode(99), 0);
    }
}
