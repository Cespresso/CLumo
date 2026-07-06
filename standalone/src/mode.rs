use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::assets;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Mode {
    Pet = 0,
    Pomodoro = 1,
    Dice = 2,
}

const MODE_COUNT: u8 = 3;

impl Mode {
    pub fn from_u8(value: u8) -> Self {
        match value {
            0 => Mode::Pet,
            1 => Mode::Pomodoro,
            2 => Mode::Dice,
            _ => Mode::Pet,
        }
    }

    pub fn name(&self) -> &'static str {
        match self {
            Mode::Pet => "Pet",
            Mode::Pomodoro => "Pomodoro",
            Mode::Dice => "Dice",
        }
    }

    pub fn next(self) -> Self {
        Mode::from_u8((self as u8 + 1) % MODE_COUNT)
    }

    pub fn icon(&self) -> [u8; 8] {
        match self {
            Mode::Pet => assets::ICON_PET,
            Mode::Pomodoro => assets::ICON_POMODORO,
            Mode::Dice => assets::ICON_DICE,
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
                log::info!("No saved mode, defaulting to Pomodoro");
                Mode::Pomodoro
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
        log::info!(
            "Mode: {} -> {}",
            self.current_mode.name(),
            new_mode.name()
        );
        self.current_mode = new_mode;
        self.nvs.set_u8("MODE", new_mode as u8)?;
        Ok(())
    }
}
