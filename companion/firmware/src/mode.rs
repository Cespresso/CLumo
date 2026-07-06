use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::assets;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Mode {
    Timer = 0,
    Display = 1,
    Visualizer = 2,
}

pub const MODE_COUNT: u8 = 3;

impl Mode {
    pub fn from_u8(value: u8) -> Self {
        match value {
            1 => Mode::Display,
            2 => Mode::Visualizer,
            _ => Mode::Timer,
        }
    }

    pub fn name(&self) -> &'static str {
        match self {
            Mode::Timer => "Timer",
            Mode::Display => "Display",
            Mode::Visualizer => "Visualizer",
        }
    }

    pub fn next(self) -> Self {
        Mode::from_u8((self as u8 + 1) % MODE_COUNT)
    }

    pub fn icon(&self) -> [u8; 8] {
        match self {
            Mode::Timer => assets::ICON_TIMER,
            Mode::Display => assets::ICON_DISPLAY,
            Mode::Visualizer => assets::ICON_VISUALIZER,
        }
    }
}

pub struct ModeManager {
    current_mode: Mode,
    nvs: EspNvs<NvsDefault>,
}

impl ModeManager {
    pub fn new(nvs: EspNvs<NvsDefault>) -> Result<Self, EspError> {
        let current_mode = match nvs.get_u8("MODE")? {
            Some(v) => {
                let mode = Mode::from_u8(v);
                log::info!("Loaded mode from NVS: {}", mode.name());
                mode
            }
            None => {
                log::info!("No saved mode, defaulting to Timer");
                Mode::Timer
            }
        };
        Ok(Self { current_mode, nvs })
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
        self.nvs.set_u8("MODE", new_mode as u8)?;
        Ok(())
    }
}
