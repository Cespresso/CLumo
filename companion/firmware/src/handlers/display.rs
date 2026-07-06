use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault};

use crate::assets;

use super::ModeHandler;

const NVS_NAMESPACE: &str = "DISPLAY";
const KEY_PATTERN: &str = "PATTERN";

/// Custom display: shows the last 8-byte row bitmap received over BLE.
/// The pattern is persisted in NVS so it survives reboots.
/// Blank until the first bitmap arrives. No button actions.
pub struct DisplayHandler {
    nvs: EspNvs<NvsDefault>,
    pattern: [u8; 8],
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
            pattern,
            dirty: false,
        })
    }
}

impl ModeHandler for DisplayHandler {
    fn on_enter(&mut self) -> [u8; 8] {
        self.dirty = false;
        self.pattern
    }

    fn on_ble_data(&mut self, data: [u8; 8]) {
        if data == self.pattern {
            return;
        }
        self.pattern = data;
        self.dirty = true;
        if let Err(e) = self.nvs.set_blob(KEY_PATTERN, &data) {
            log::warn!("Display: failed to persist pattern: {:?}", e);
        }
    }

    fn tick(&mut self) -> Option<[u8; 8]> {
        if self.dirty {
            self.dirty = false;
            Some(self.pattern)
        } else {
            None
        }
    }
}
