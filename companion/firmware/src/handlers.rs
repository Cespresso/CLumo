pub mod display;
pub mod timer;
pub mod visualizer;

use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::EspDefaultNvsPartition;

use crate::mode::Mode;
use crate::utils::bluetooth::TimerCommand;
use crate::utils::button::PressType;

/// Common interface for mode-specific behavior.
/// Each mode implements this trait to handle buttons, BLE data, and display updates.
pub trait ModeHandler {
    /// Called when entering this mode. Returns the initial frame to display.
    fn on_enter(&mut self) -> [u8; 8];

    /// Called on red button press.
    fn on_red_button(&mut self, _press: PressType) {}

    /// Called on white button press (short only; long is consumed by mode cycling).
    fn on_white_button(&mut self, _press: PressType) {}

    /// Called when BLE display data is received.
    fn on_ble_data(&mut self, _data: [u8; 8]) {}

    /// Called when a BLE timer command is received. Only Timer mode reacts.
    fn on_timer_command(&mut self, _cmd: TimerCommand) {}

    /// Returns a timer status frame pending delivery to BLE clients, if any.
    /// Only Timer mode produces status frames.
    fn poll_timer_status(&mut self) -> Option<[u8; 6]> {
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
        Mode::Timer => Box::new(timer::TimerHandler::new(nvs_partition)?),
        Mode::Display => Box::new(display::DisplayHandler::new(nvs_partition)?),
        Mode::Visualizer => Box::new(visualizer::VisualizerHandler::new()),
    };
    Ok(handler)
}
