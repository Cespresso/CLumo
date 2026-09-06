use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};

use crate::assets;
use crate::display_state::DisplayState;

use super::ModeHandler;

const NVS_NAMESPACE: &str = "DISPLAY";
const KEY_PATTERN: &str = "PATTERN";
const PREVIEW_TTL_MS: u64 = 5_000;

/// Custom display: shows the last 8-byte row bitmap received over BLE.
/// The pattern is persisted in NVS so it survives reboots.
/// Blank until the first bitmap arrives. No button actions.
pub struct DisplayHandler {
    nvs: EspNvs<NvsDefault>,
    state: DisplayState,
    started_at: std::time::Instant,
    dirty: bool,
}

impl DisplayHandler {
    pub fn new(nvs_partition: EspDefaultNvsPartition) -> Result<Self, EspError> {
        let nvs = EspNvs::new(nvs_partition, NVS_NAMESPACE, true)?;

        let mut pattern = assets::PATTERN_ALL_OFF;
        let mut buf = [0u8; 8];
        if let Ok(Some(bytes)) = nvs.get_blob(KEY_PATTERN, &mut buf) {
            if bytes.len() == 8 {
                pattern = buf;
            }
        }

        Ok(Self {
            nvs,
            state: DisplayState::new(pattern),
            started_at: std::time::Instant::now(),
            dirty: false,
        })
    }

    pub fn commit_preview(&mut self) {
        let previous = self.state.committed_frame();
        let Some(committed) = self.state.commit_preview() else {
            return;
        };
        self.dirty = true;
        if committed != previous {
            if let Err(e) = self.nvs.set_blob(KEY_PATTERN, &committed) {
                log::warn!("Display: failed to persist committed pattern: {:?}", e);
            }
        }
    }

    pub fn cancel_preview(&mut self) {
        if self.state.cancel_preview() {
            self.dirty = true;
        }
    }

    fn now_ms(&self) -> u64 {
        self.started_at.elapsed().as_millis() as u64
    }
}

impl ModeHandler for DisplayHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        self.dirty = false;
        self.state.visible_frame()
    }

    fn on_ble_data(&mut self, data: [u8; 8]) {
        self.state.preview(data, self.now_ms());
        self.dirty = true;
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        if self.state.expire_preview(self.now_ms(), PREVIEW_TTL_MS) {
            self.dirty = true;
        }
        if self.dirty {
            self.dirty = false;
            Some(self.state.visible_frame())
        } else {
            None
        }
    }
}
