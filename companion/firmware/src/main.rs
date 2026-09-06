use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs};

use crate::handlers::timer;
use crate::mode::Mode;
use crate::utils::bluetooth::{BleCommand, BluetoothManager};
use crate::utils::button::{Buttons, PressType};
use crate::utils::device_id;
use crate::utils::led::Display;

mod assets;
mod handlers;
mod mode;
mod utils;

/// How long the mode icon splash stays on screen when switching modes (ms).
const MODE_SPLASH_MS: u32 = 500;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    esp_idf_svc::sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();
    log::info!("=== CLumo Companion Starting ===");

    let peripherals = Peripherals::take().unwrap();
    FreeRtos::delay_ms(200);

    // Initialize LED matrix (MAX7219 via SPI)
    let mut display = Display::new(
        peripherals.spi2,
        peripherals.pins.gpio8.into(),
        peripherals.pins.gpio9.into(),
        peripherals.pins.gpio10.into(),
    )?;
    log::info!("LED matrix initialized");

    // Initialize NVS, device identity, and Mode Manager
    let nvs_partition = EspDefaultNvsPartition::take()?;
    let mut nvs_device = EspNvs::new(nvs_partition.clone(), "DEVICE", true)?;
    let device_id = device_id::load_or_create(&mut nvs_device)?;
    let nvs_mode = EspNvs::new(nvs_partition.clone(), "STATE", true)?;
    let mut mode_manager = mode::ModeManager::new(nvs_mode)?;
    log::info!("Mode system initialized: {}", mode_manager.current().name());

    // Seed the TIMER characteristic with an idle status so reads before the
    // first status push are already valid.
    let mut last_timer_status = {
        let nvs_timer = EspNvs::new(nvs_partition.clone(), timer::NVS_NAMESPACE, true)?;
        let (work_min, break_min) = timer::load_durations(&nvs_timer);
        timer::idle_status(work_min, break_min)
    };

    // Initialize BLE
    let ble = BluetoothManager::init(mode_manager.current() as u8, last_timer_status, device_id)?;
    log::info!("BLE initialized");

    // Initialize buttons (red=GPIO3, white=GPIO4)
    let mut buttons = Buttons::new(
        peripherals.pins.gpio3.into(),
        peripherals.pins.gpio4.into(),
    )?;
    log::info!("Buttons initialized (red=GPIO3, white=GPIO4)");

    // Create handler for current mode
    let mut handler = handlers::create_handler(mode_manager.current(), nvs_partition.clone())?;
    display.show(&handler.on_enter());

    // Main loop
    loop {
        let red_press = buttons.red.poll();
        let white_press = buttons.white.poll();

        // Drain BLE commands
        while let Some(cmd) = ble.take_command() {
            match cmd {
                BleCommand::SwitchMode(m) => {
                    let new_mode = Mode::from_u8(m);
                    if new_mode == mode_manager.current() {
                        continue;
                    }
                    if mode_manager.current() == Mode::Timer {
                        // Leaving Timer mode drops the countdown; report idle.
                        last_timer_status =
                            timer::idle_status(last_timer_status[4], last_timer_status[5]);
                        ble.notify_timer_status(&last_timer_status);
                    }
                    if let Err(e) = mode_manager.switch_to(new_mode) {
                        log::error!("BLE switch_to failed: {:?}", e);
                    }
                    ble.notify_mode_change(mode_manager.current() as u8);
                    handler =
                        handlers::create_handler(mode_manager.current(), nvs_partition.clone())?;
                    display.show(&mode_manager.current().icon());
                    FreeRtos::delay_ms(MODE_SPLASH_MS);
                    display.show(&handler.on_enter());
                }
                BleCommand::SetDisplayData(data) => {
                    handler.on_ble_data(data);
                }
                BleCommand::SetBrightness(level) => {
                    display.set_intensity(level);
                    ble.notify_brightness_change(level);
                    log::info!("Brightness set to {}", level);
                }
                BleCommand::Timer(timer_cmd) => {
                    handler.on_timer_command(timer_cmd);
                    if mode_manager.current() != Mode::Timer {
                        // Timer commands are ignored outside Timer mode; restore
                        // the status value so READ stays accurate.
                        ble.set_timer_status(&last_timer_status);
                    }
                }
            }
        }

        // White long-press: cycle mode (universal, consumed before mode dispatch)
        let white_press = if let Some(PressType::Long) = white_press {
            if mode_manager.current() == Mode::Timer {
                // Leaving Timer mode drops the countdown; report idle.
                last_timer_status =
                    timer::idle_status(last_timer_status[4], last_timer_status[5]);
                ble.notify_timer_status(&last_timer_status);
            }
            let next = mode_manager.current().next();
            if let Err(e) = mode_manager.switch_to(next) {
                log::error!("Failed to switch mode: {:?}", e);
            }
            ble.notify_mode_change(mode_manager.current() as u8);
            handler = handlers::create_handler(mode_manager.current(), nvs_partition.clone())?;
            display.show(&mode_manager.current().icon());
            FreeRtos::delay_ms(MODE_SPLASH_MS);
            display.show(&handler.on_enter());
            None // consume the press
        } else {
            white_press
        };

        // Mode-specific button handling
        if let Some(press) = red_press {
            log::info!("[{}] Red: {:?}", mode_manager.current().name(), press);
            handler.on_red_button(press);
        }
        if let Some(press) = white_press {
            log::info!("[{}] White: {:?}", mode_manager.current().name(), press);
            handler.on_white_button(press);
        }

        // Display update
        if let Some(frame) = handler.tick() {
            display.show(&frame);
        }

        // Timer status: pushed on state/phase changes and once per second
        // while running (Timer mode only).
        if let Some(status) = handler.poll_timer_status() {
            last_timer_status = status;
            ble.notify_timer_status(&status);
        }

        FreeRtos::delay_ms(50);
    }
}
