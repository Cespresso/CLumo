use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::mode_values::{decode_mode, resolve_boot_mode};
use crate::settings_values::decode_brightness;

pub use crate::mode_values::MODE_COUNT;

/// Current-schema mode value. The only mode key ever written.
const MODE_KEY: &str = "MODE2";
const LEGACY_MODE_KEY: &str = "MODE";
const LEGACY_MODE_SCHEMA_KEY: &str = "MODE_SCHEMA";
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
        let legacy_mode = nvs.get_u8(LEGACY_MODE_KEY)?;
        let legacy_schema = nvs.get_u8(LEGACY_MODE_SCHEMA_KEY)?;
        let mode_value = resolve_boot_mode(stored_mode, legacy_mode, legacy_schema);
        let current_mode = Mode::from_u8(mode_value);

        if stored_mode.is_none() {
            nvs.set_u8(MODE_KEY, mode_value)?;
            match legacy_mode {
                Some(_) => log::info!("Migrated mode from legacy NVS: {}", current_mode.name()),
                None => log::info!("No saved mode, defaulting to {}", current_mode.name()),
            }
        } else {
            log::info!("Loaded mode from NVS: {}", current_mode.name());
        }

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
