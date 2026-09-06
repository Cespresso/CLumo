pub const PIXELS_TOTAL: u64 = 64;

/// Lit pixels `elapsed_ms` into a phase of `duration_ms`. Every phase counts
/// down from a full matrix, break included.
pub fn lit_pixels(elapsed_ms: u64, duration_ms: u64) -> u8 {
    if duration_ms == 0 {
        return 0;
    }
    let passed = elapsed_ms.min(duration_ms) * PIXELS_TOTAL / duration_ms;
    (PIXELS_TOTAL - passed) as u8
}

/// An 8x8 frame whose lit pixels occupy the row-major suffix, so pixels turn
/// off from the top-left toward the bottom-right.
pub fn progress_frame(lit: u8) -> [u8; 8] {
    let lit = lit.min(PIXELS_TOTAL as u8);
    let mut frame = [0u8; 8];
    let full_rows = (lit / 8) as usize;
    let remainder = lit % 8;

    for row in frame.iter_mut().rev().take(full_rows) {
        *row = 0xFF;
    }
    if full_rows < 8 && remainder > 0 {
        frame[7 - full_rows] = (1 << remainder) - 1;
    }
    frame
}

#[cfg(test)]
mod tests {
    use super::*;

    const WORK_MS: u64 = 25 * 60 * 1000;

    #[test]
    fn a_phase_starts_full_and_ends_empty() {
        assert_eq!(lit_pixels(0, WORK_MS), 64);
        assert_eq!(lit_pixels(WORK_MS / 2, WORK_MS), 32);
        assert_eq!(lit_pixels(WORK_MS, WORK_MS), 0);
    }

    #[test]
    fn overrunning_the_phase_does_not_run_past_empty() {
        assert_eq!(lit_pixels(WORK_MS * 3, WORK_MS), 0);
    }

    #[test]
    fn an_idle_phase_has_no_duration_to_measure_against() {
        assert_eq!(lit_pixels(0, 0), 0);
        assert_eq!(lit_pixels(1_000, 0), 0);
    }

    #[test]
    fn pixels_turn_off_from_the_top_left_toward_the_bottom_right() {
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
    fn a_count_past_the_matrix_lights_it_whole_rather_than_wrapping() {
        assert_eq!(progress_frame(u8::MAX), [0xFF; 8]);
    }
}
