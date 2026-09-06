pub const PIXELS_TOTAL: u64 = 64;

/// Lit pixels `elapsed_ms` into a phase of `duration_ms`. A draining phase
/// starts from a full matrix and empties; a filling one does the reverse.
pub fn lit_pixels(elapsed_ms: u64, duration_ms: u64, draining: bool) -> u8 {
    if duration_ms == 0 {
        return 0;
    }
    let passed = elapsed_ms.min(duration_ms) * PIXELS_TOTAL / duration_ms;
    if draining {
        (PIXELS_TOTAL - passed) as u8
    } else {
        passed as u8
    }
}

/// An 8x8 frame with `lit` pixels on, occupying the row-major prefix: row 0
/// first, and within a row from the most significant bit.
pub fn progress_frame(lit: u8) -> [u8; 8] {
    let lit = lit.min(PIXELS_TOTAL as u8);
    let mut frame = [0u8; 8];
    let full_rows = (lit / 8) as usize;
    let remainder = lit % 8;

    for row in frame.iter_mut().take(full_rows) {
        *row = 0xFF;
    }
    if full_rows < 8 && remainder > 0 {
        frame[full_rows] = 0xFF << (8 - remainder);
    }
    frame
}

#[cfg(test)]
mod tests {
    use super::*;

    const WORK_MS: u64 = 25 * 60 * 1000;

    #[test]
    fn a_draining_phase_starts_full_and_ends_empty() {
        assert_eq!(lit_pixels(0, WORK_MS, true), 64);
        assert_eq!(lit_pixels(WORK_MS / 2, WORK_MS, true), 32);
        assert_eq!(lit_pixels(WORK_MS, WORK_MS, true), 0);
    }

    #[test]
    fn a_filling_phase_starts_empty_and_ends_full() {
        assert_eq!(lit_pixels(0, WORK_MS, false), 0);
        assert_eq!(lit_pixels(WORK_MS / 2, WORK_MS, false), 32);
        assert_eq!(lit_pixels(WORK_MS, WORK_MS, false), 64);
    }

    #[test]
    fn overrunning_the_phase_does_not_run_past_the_ends() {
        assert_eq!(lit_pixels(WORK_MS * 3, WORK_MS, true), 0);
        assert_eq!(lit_pixels(WORK_MS * 3, WORK_MS, false), 64);
    }

    #[test]
    fn an_idle_phase_has_no_duration_to_measure_against() {
        assert_eq!(lit_pixels(0, 0, true), 0);
        assert_eq!(lit_pixels(1_000, 0, false), 0);
    }

    #[test]
    fn lit_pixels_fill_row_major_from_the_first_row() {
        assert_eq!(progress_frame(64), [0xFF; 8]);
        assert_eq!(
            progress_frame(32),
            [0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00]
        );
        assert_eq!(
            progress_frame(9),
            [0xFF, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]
        );
        assert_eq!(
            progress_frame(1),
            [0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]
        );
        assert_eq!(progress_frame(0), [0x00; 8]);
    }

    #[test]
    fn a_count_past_the_matrix_lights_it_whole_rather_than_wrapping() {
        assert_eq!(progress_frame(u8::MAX), [0xFF; 8]);
    }
}
