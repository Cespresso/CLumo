use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::mode_values::{decode_mode, migrate_legacy_mode};
use crate::settings_values::decode_brightness;

pub use crate::mode_values::MODE_COUNT;

const MODE_KEY: &str = "MODE";
const MODE_SCHEMA_KEY: &str = "MODE_SCHEMA";
const MODE_SCHEMA_VERSION: u8 = 2;
const BRIGHTNESS_KEY: &str = "BRIGHT";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Mode {
    Pomodoro = 0,
    Timer = 1,
    Display = 2,
    Visualizer = 3,
}

impl Mode {
    pub fn from_u8(value: u8) -> Self {
        match decode_mode(value) {
            1 => Mode::Timer,
            2 => Mode::Display,
            3 => Mode::Visualizer,
            _ => Mode::Pomodoro,
        }
    }

    pub fn name(&self) -> &'static str {
        match self {
            Mode::Pomodoro => "Pomodoro",
            Mode::Timer => "Timer",
            Mode::Display => "Display",
            Mode::Visualizer => "Visualizer",
        }
    }
}

pub struct ModeManager {
    current_mode: Mode,
    brightness: u8,
    nvs: EspNvs<NvsDefault>,
}

impl ModeManager {
    pub fn new(nvs: EspNvs<NvsDefault>) -> Result<Self, EspError> {
        let stored_mode = nvs.get_u8(MODE_KEY)?;
        let schema = nvs.get_u8(MODE_SCHEMA_KEY)?.unwrap_or(1);
        let mode_value = if schema < MODE_SCHEMA_VERSION {
            let migrated = stored_mode.map(migrate_legacy_mode).unwrap_or(0);
            if stored_mode.is_some() {
                nvs.set_u8(MODE_KEY, migrated)?;
            }
            nvs.set_u8(MODE_SCHEMA_KEY, MODE_SCHEMA_VERSION)?;
            migrated
        } else {
            stored_mode.unwrap_or(0)
        };
        let current_mode = match stored_mode {
            Some(_) => {
                let mode = Mode::from_u8(mode_value);
                log::info!("Loaded mode from NVS: {}", mode.name());
                mode
            }
            None => {
                log::info!("No saved mode, defaulting to Pomodoro");
                Mode::Pomodoro
            }
        };
        let brightness = decode_brightness(nvs.get_u8(BRIGHTNESS_KEY)?);
        Ok(Self {
            current_mode,
            brightness,
            nvs,
        })
    }

    pub fn current(&self) -> Mode {
        self.current_mode
    }

    pub fn switch_to(&mut self, new_mode: Mode) -> Result<(), EspError> {
        if self.current_mode == new_mode {
            return Ok(());
        }
        log::info!("Mode: {} -> {}", self.current_mode.name(), new_mode.name());
        self.current_mode = new_mode;
        self.nvs.set_u8(MODE_KEY, new_mode as u8)?;
        Ok(())
    }

    pub fn brightness(&self) -> u8 {
        self.brightness
    }

    pub fn set_brightness(&mut self, level: u8) -> Result<u8, EspError> {
        let level = level.min(15);
        self.nvs.set_u8(BRIGHTNESS_KEY, level)?;
        self.brightness = level;
        Ok(level)
    }
}
