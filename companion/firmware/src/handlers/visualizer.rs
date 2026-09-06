use std::time::Instant;

use crate::assets;

use super::ModeHandler;

/// Converts column heights (0-8 each) into row data for the 8x8 LED matrix.
/// Column 0 = MSB (bit 7), Column 7 = LSB (bit 0).
fn columns_to_rows(heights: &[u8; 8]) -> [u8; 8] {
    let mut rows = [0u8; 8];
    for col in 0..8 {
        let h = heights[col].min(8) as usize;
        for row in (8 - h)..8 {
            rows[row] |= 1 << (7 - col);
        }
    }
    rows
}

/// Interval between gravity fall steps (ms).
const GRAVITY_STEP_MS: u64 = 80;
/// Time after last BLE data before idle decay starts (ms).
const IDLE_AFTER_MS: u64 = 300;

/// Audio visualizer: renders BLE column heights as bars.
///
/// Uses a gravity model: bars rise instantly to match incoming data,
/// but fall at most 1 step per GRAVITY_STEP_MS (smooth descent).
/// When BLE data stops arriving, bars decay to zero. No button actions.
pub struct VisualizerHandler {
    /// Target heights from latest BLE data.
    target: [u8; 8],
    /// Displayed heights (approaches target with gravity).
    display: [u8; 8],
    last_gravity: Instant,
    last_receive: Instant,
    dirty: bool,
}

impl VisualizerHandler {
    pub fn new() -> Self {
        let now = Instant::now();
        Self {
            target: [0u8; 8],
            display: [0u8; 8],
            last_gravity: now,
            last_receive: now,
            dirty: false,
        }
    }
}

impl ModeHandler for VisualizerHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        assets::ICON_VISUALIZER
    }

    fn on_ble_data(&mut self, data: [u8; 8]) {
        for i in 0..8 {
            let incoming = data[i].min(8);
            self.target[i] = incoming;
            // Rise instantly
            if incoming > self.display[i] {
                self.display[i] = incoming;
                self.dirty = true;
            }
        }
        self.last_receive = Instant::now();
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        let since_gravity = self.last_gravity.elapsed().as_millis() as u64;

        if since_gravity >= GRAVITY_STEP_MS {
            self.last_gravity = Instant::now();

            let since_receive = self.last_receive.elapsed().as_millis() as u64;
            let idle = since_receive >= IDLE_AFTER_MS;

            for i in 0..8 {
                let fall_target = if idle { 0 } else { self.target[i] };
                if self.display[i] > fall_target {
                    self.display[i] -= 1;
                    self.dirty = true;
                }
            }
        }

        if self.dirty {
            self.dirty = false;
            Some(columns_to_rows(&self.display))
        } else {
            None
        }
    }
}
