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

    /// Called on a main (red) button press.
    fn on_main_button(&mut self) {}

    /// Called on a sub (white) button press.
    fn on_sub_button(&mut self) {}

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

/// Long-lived owner of every mode handler.
/// Switching modes only changes which frame is rendered.
/// Countdowns and configuration stay alive for the process lifetime.
pub struct Runtime {
    pomodoro: pomodoro::PomodoroHandler,
    timer: timer::TimerHandler,
    display: display::DisplayHandler,
    visualizer: visualizer::VisualizerHandler,
}

impl Runtime {
    pub fn new(nvs_partition: EspDefaultNvsPartition) -> Result<Self, EspError> {
        Ok(Self {
            pomodoro: pomodoro::PomodoroHandler::new(nvs_partition.clone())?,
            timer: timer::TimerHandler::new(nvs_partition.clone())?,
            display: display::DisplayHandler::new(nvs_partition)?,
            visualizer: visualizer::VisualizerHandler::new(),
        })
    }

    pub fn on_enter(&mut self, mode: Mode) -> [u8; 8] {
        match mode {
            Mode::Pomodoro => self.pomodoro.on_enter(),
            Mode::Timer => self.timer.on_enter(),
            Mode::Display => self.display.on_enter(),
            Mode::Visualizer => self.visualizer.on_enter(),
        }
    }

    pub fn on_button(&mut self, mode: Mode, is_main: bool) {
        let handler: &mut dyn ModeHandler = match mode {
            Mode::Pomodoro => &mut self.pomodoro,
            Mode::Timer => &mut self.timer,
            Mode::Display => &mut self.display,
            Mode::Visualizer => &mut self.visualizer,
        };
        if is_main {
            handler.on_main_button();
        } else {
            handler.on_sub_button();
        }
    }

    /// A DISPLAY_PREVIEW write. The caller has already checked the mode.
    pub fn on_display_preview(&mut self, frame: [u8; 8]) {
        self.display.on_preview(frame);
    }

    /// A VISUALIZER write. The caller has already checked the mode.
    pub fn on_visualizer_columns(&mut self, heights: [u8; 8]) {
        self.visualizer.on_columns(heights);
    }

    /// A DISPLAY_FRAME write: commits `frame`.
    pub fn commit_display(&mut self, frame: [u8; 8]) {
        self.display.commit(frame);
    }

    pub fn cancel_display_preview(&mut self) {
        self.display.cancel_preview();
    }

    /// The committed Display frame, as opposed to any live preview.
    pub fn display_committed_frame(&self) -> [u8; 8] {
        self.display.committed_frame()
    }

    pub fn on_pomodoro_command(&mut self, command: PomodoroCommand) {
        self.pomodoro.on_pomodoro_command(command);
    }

    pub fn on_timer_command(&mut self, command: TimerCommand) {
        self.timer.on_timer_command(command);
    }

    pub fn tick(&mut self, active_mode: Mode) -> Option<[u8; 8]> {
        let pomodoro = self.pomodoro.tick();
        let timer = self.timer.tick();
        let display = self.display.tick();
        let visualizer = self.visualizer.tick();
        match active_mode {
            Mode::Pomodoro => pomodoro,
            Mode::Timer => timer,
            Mode::Display => display,
            Mode::Visualizer => visualizer,
        }
    }

    pub fn poll_pomodoro_status(&mut self) -> Option<[u8; 6]> {
        self.pomodoro.poll_pomodoro_status()
    }

    pub fn poll_timer_status(&mut self) -> Option<[u8; 5]> {
        self.timer.poll_timer_status()
    }

    pub fn pomodoro_status(&self) -> [u8; 6] {
        self.pomodoro.current_status()
    }

    pub fn timer_status(&self) -> [u8; 5] {
        self.timer.current_status()
    }
}
