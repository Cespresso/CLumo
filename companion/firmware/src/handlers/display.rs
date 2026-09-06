use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};

use crate::assets;
use crate::display_state::DisplayState;

use super::ModeHandler;

const NVS_NAMESPACE: &str = "DISPLAY";
const KEY_PATTERN: &str = "PATTERN";
const PREVIEW_TTL_MS: u64 = 5_000;

/// Custom display: shows the frame committed over DISPLAY_FRAME, with live previews
/// from DISPLAY_PREVIEW drawn on top until they expire. The committed frame is
/// persisted in NVS so it survives reboots. Blank until the first commit arrives.
/// No button actions.
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

    /// Makes `frame` the committed frame, persisting it when it changed and
    /// discarding any live preview.
    pub fn commit(&mut self, frame: [u8; 8]) {
        let changed = self.state.commit(frame);
        // Redraw even when unchanged: a preview may have been covering this frame.
        self.dirty = true;
        if changed {
            if let Err(e) = self.nvs.set_blob(KEY_PATTERN, &frame) {
                log::warn!("Display: failed to persist committed pattern: {:?}", e);
            }
        }
    }

    /// A live preview: shown at once, never persisted, gone after the TTL.
    pub fn on_preview(&mut self, frame: [u8; 8]) {
        self.state.preview(frame, self.now_ms());
        self.dirty = true;
    }

    pub fn committed_frame(&self) -> [u8; 8] {
        self.state.committed_frame()
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
