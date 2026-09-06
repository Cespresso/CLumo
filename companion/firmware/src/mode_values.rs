// The MODE characteristic's values, shared with the Android companion.
pub const MODE_POMODORO: u8 = 0;
pub const MODE_TIMER: u8 = 1;
pub const MODE_DISPLAY: u8 = 2;
pub const MODE_VISUALIZER: u8 = 3;

pub const MODE_COUNT: u8 = 4;

/// Schema version at which the protocol v2 mode values became current.
pub const MODE_SCHEMA_VERSION: u8 = 2;

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

/// Resolve the mode to boot into from what NVS holds.
///
/// `migrate_legacy_mode` maps 1 to 2 and 2 to 3, so running it on its own output
/// shifts the mode again. The `legacy` inputs must never be overwritten with the
/// result, which is what keeps an interrupted boot recomputing the same answer.
pub fn resolve_boot_mode(stored: Option<u8>, legacy: Option<u8>, legacy_schema: Option<u8>) -> u8 {
    if let Some(value) = stored {
        return decode_mode(value);
    }
    match legacy {
        None => 0,
        // A schema this high means the legacy key was already rewritten in place,
        // so its value is current and migrating it again would shift the mode.
        Some(value) if legacy_schema.unwrap_or(1) >= MODE_SCHEMA_VERSION => decode_mode(value),
        Some(value) => migrate_legacy_mode(value),
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

    #[test]
    fn current_value_wins_over_the_legacy_keys() {
        assert_eq!(resolve_boot_mode(Some(3), Some(1), None), 3);
        assert_eq!(resolve_boot_mode(Some(0), Some(2), Some(2)), 0);
        assert_eq!(resolve_boot_mode(Some(99), Some(1), None), 0);
    }

    #[test]
    fn fresh_device_boots_into_pomodoro() {
        assert_eq!(resolve_boot_mode(None, None, None), 0);
        assert_eq!(resolve_boot_mode(None, None, Some(2)), 0);
    }

    #[test]
    fn v1_values_are_migrated_once() {
        assert_eq!(resolve_boot_mode(None, Some(1), None), 2);
        assert_eq!(resolve_boot_mode(None, Some(2), None), 3);
        assert_eq!(resolve_boot_mode(None, Some(1), Some(1)), 2);
    }

    #[test]
    fn values_already_migrated_in_place_are_not_migrated_again() {
        // Migrating these again would turn Display into Visualizer.
        assert_eq!(resolve_boot_mode(None, Some(2), Some(2)), 2);
        assert_eq!(resolve_boot_mode(None, Some(3), Some(2)), 3);
    }

    #[test]
    fn derivation_is_idempotent_because_legacy_inputs_never_change() {
        for legacy in 0..=3u8 {
            for schema in [None, Some(1), Some(2)] {
                let first = resolve_boot_mode(None, Some(legacy), schema);
                let second = resolve_boot_mode(None, Some(legacy), schema);
                assert_eq!(first, second);
                // Later boots read the derived value back; that must not shift it.
                assert_eq!(resolve_boot_mode(Some(first), Some(legacy), schema), first);
            }
        }
    }
}
