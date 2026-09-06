use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::assets;
use crate::mode_values::{decode_mode, next_mode, MODE_DICE, MODE_PET, MODE_POMODORO};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Mode {
    Pet = 0,
    Pomodoro = 1,
    Dice = 2,
}

impl Mode {
    pub fn from_u8(value: u8) -> Self {
        match decode_mode(value) {
            MODE_PET => Mode::Pet,
            MODE_POMODORO => Mode::Pomodoro,
            MODE_DICE => Mode::Dice,
            // Unreachable after decode_mode, which folds anything out of range to MODE_DEFAULT.
            _ => Mode::Pomodoro,
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
        Mode::from_u8(next_mode(self as u8))
    }

    pub fn icon(&self) -> [u8; 8] {
        match self {
            Mode::Pet => assets::ICON_PET,
            Mode::Pomodoro => assets::ICON_POMODORO,
            Mode::Dice => assets::ICON_DICE,
        }
    }
}

// The enum and the persisted values name the same three numbers; a drift between
// them would boot the device into a mode the NVS value does not mean.
const _: () = assert!(
    Mode::Pet as u8 == MODE_PET
        && Mode::Pomodoro as u8 == MODE_POMODORO
        && Mode::Dice as u8 == MODE_DICE
);

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
                let mode = Mode::from_u8(crate::mode_values::MODE_DEFAULT);
                log::info!("No saved mode, defaulting to {}", mode.name());
                mode
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
