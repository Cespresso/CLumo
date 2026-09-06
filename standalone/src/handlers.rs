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

/// Create the appropriate handler for the given mode.
pub fn create_handler(mode: Mode) -> Box<dyn ModeHandler> {
    match mode {
        Mode::Pet => Box::new(pet::PetHandler::new()),
        Mode::Pomodoro => Box::new(pomodoro::PomodoroHandler::new()),
        Mode::Dice => Box::new(dice::DiceHandler::new()),
    }
}
