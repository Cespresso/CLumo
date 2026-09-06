use crate::mode_values::{MODE_DISPLAY, MODE_VISUALIZER};

/// A write to one of the two display streams, told apart by UUID on the BLE side.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DisplayWrite {
    /// DISPLAY_PREVIEW: volatile, expires on the device.
    Preview,
    /// VISUALIZER: volatile column heights.
    Visualizer,
}

/// Whether the firmware acts on `write` while `mode` is on the matrix.
/// DISPLAY_FRAME is not routed here: a commit is state, accepted in every mode, and
/// never touches the matrix by itself.
pub fn accepts(mode: u8, write: DisplayWrite) -> bool {
    match write {
        DisplayWrite::Preview => mode == MODE_DISPLAY,
        DisplayWrite::Visualizer => mode == MODE_VISUALIZER,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::mode_values::{MODE_POMODORO, MODE_TIMER};

    #[test]
    fn preview_is_accepted_only_in_display() {
        assert!(accepts(MODE_DISPLAY, DisplayWrite::Preview));
        for mode in [MODE_POMODORO, MODE_TIMER, MODE_VISUALIZER] {
            assert!(!accepts(mode, DisplayWrite::Preview), "mode {mode}");
        }
    }

    #[test]
    fn visualizer_columns_are_accepted_only_in_visualizer() {
        assert!(accepts(MODE_VISUALIZER, DisplayWrite::Visualizer));
        for mode in [MODE_POMODORO, MODE_TIMER, MODE_DISPLAY] {
            assert!(!accepts(mode, DisplayWrite::Visualizer), "mode {mode}");
        }
    }
}
