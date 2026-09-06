pub mod dice;
pub mod pet;
pub mod pomodoro;

use crate::mode::Mode;
use crate::utils::button::PressType;

/// Common interface for mode-specific behavior.
/// Each mode implements this trait to handle buttons and display updates.
pub trait ModeHandler {
    /// Called when entering this mode. Returns the initial frame to display.
    fn on_enter(&mut self) -> [u8; 8];

    /// Called on red button press.
    fn on_red_button(&mut self, _press: PressType) {}

    /// Called on white button press.
    fn on_white_button(&mut self, _press: PressType) {}

    /// Called every tick (~50ms). Returns Some(frame) if display should update.
    fn tick(&mut self) -> Option<[u8; 8]>;
}

/// Long-lived owner of every mode handler.
/// Switching modes only changes which frame is rendered: a running pomodoro
/// keeps counting and the pet keeps getting hungry while another mode is up.
pub struct Runtime {
    pet: pet::PetHandler,
    pomodoro: pomodoro::PomodoroHandler,
    dice: dice::DiceHandler,
}

impl Runtime {
    pub fn new() -> Self {
        Self {
            pet: pet::PetHandler::new(),
            pomodoro: pomodoro::PomodoroHandler::new(),
            dice: dice::DiceHandler::new(),
        }
    }

    pub fn on_enter(&mut self, mode: Mode) -> [u8; 8] {
        self.handler(mode).on_enter()
    }

    pub fn on_red_button(&mut self, mode: Mode, press: PressType) {
        self.handler(mode).on_red_button(press);
    }

    pub fn on_white_button(&mut self, mode: Mode, press: PressType) {
        self.handler(mode).on_white_button(press);
    }

    /// Ticks every mode, so time passes for all of them, and returns the frame
    /// of the one currently on the matrix.
    pub fn tick(&mut self, active_mode: Mode) -> Option<[u8; 8]> {
        let pet = self.pet.tick();
        let pomodoro = self.pomodoro.tick();
        let dice = self.dice.tick();
        match active_mode {
            Mode::Pet => pet,
            Mode::Pomodoro => pomodoro,
            Mode::Dice => dice,
        }
    }

    fn handler(&mut self, mode: Mode) -> &mut dyn ModeHandler {
        match mode {
            Mode::Pet => &mut self.pet,
            Mode::Pomodoro => &mut self.pomodoro,
            Mode::Dice => &mut self.dice,
        }
    }
}
