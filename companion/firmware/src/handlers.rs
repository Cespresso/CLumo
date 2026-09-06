pub mod display;
pub mod pomodoro;
pub mod timer;
pub mod visualizer;

use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::EspDefaultNvsPartition;

use crate::mode::Mode;
use crate::utils::bluetooth::{PomodoroCommand, TimerCommand};

/// Common interface for mode-specific behavior.
/// Each mode implements this trait to handle buttons, BLE data, and display updates.
pub trait ModeHandler {
    /// Called when entering this mode. Returns the initial frame to display.
    fn on_enter(&mut self) -> [u8; 8];

    /// Called on a main (red) button press. Display and Visualizer never receive
    /// button presses; the firmware forwards theirs to the app instead.
    fn on_main_button(&mut self) {}

    /// Called on a sub (white) button press.
    fn on_sub_button(&mut self) {}

    /// Called when BLE display data is received.
    fn on_ble_data(&mut self, _data: [u8; 8]) {}

    /// Called when a BLE pomodoro command is received. Only Pomodoro mode reacts.
    fn on_pomodoro_command(&mut self, _cmd: PomodoroCommand) {}

    /// Returns a pomodoro status frame pending delivery to BLE clients, if any.
    /// Only Pomodoro mode produces status frames.
    fn poll_pomodoro_status(&mut self) -> Option<[u8; 6]> {
        None
    }

    /// Called when a BLE countdown timer command is received. Only Timer mode reacts.
    fn on_timer_command(&mut self, _cmd: TimerCommand) {}

    /// Returns a countdown timer status frame pending delivery to BLE clients, if any.
    /// Only Timer mode produces status frames.
    fn poll_timer_status(&mut self) -> Option<[u8; 5]> {
        None
    }

    /// Called every tick (~50ms). Returns Some(frame) if display should update.
    fn tick(&mut self) -> Option<[u8; 8]>;
}

/// Create the appropriate handler for the given mode.
pub fn create_handler(
    mode: Mode,
    nvs_partition: EspDefaultNvsPartition,
) -> Result<Box<dyn ModeHandler>, EspError> {
    let handler: Box<dyn ModeHandler> = match mode {
        Mode::Pomodoro => Box::new(pomodoro::PomodoroHandler::new(nvs_partition)?),
        Mode::Timer => Box::new(timer::TimerHandler::new()),
        Mode::Display => Box::new(display::DisplayHandler::new(nvs_partition)?),
        Mode::Visualizer => Box::new(visualizer::VisualizerHandler::new()),
    };
    Ok(handler)
}
