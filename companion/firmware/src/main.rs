use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs};
use std::time::Instant;

use crate::handlers::{pomodoro, timer};
use crate::mode::Mode;
use crate::utils::bluetooth::{BleCommand, BluetoothManager};
use crate::utils::button::Buttons;
use crate::utils::device_id;
use crate::utils::led::Display;

mod assets;
mod countdown;
mod handlers;
mod mode;
mod mode_values;
mod utils;
mod visualizer_values;

/// Blink interval for the disconnected icon during connection setup (ms).
const CONNECTION_BLINK_MS: u128 = 400;

fn disconnected_icon(bonded: bool) -> &'static [u8; 8] {
    if bonded {
        &assets::ICON_DISCONNECTED_BONDED
    } else {
        &assets::ICON_DISCONNECTED
    }
}

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
    display.show(&assets::ICON_DISCONNECTED);

    // Initialize NVS, device identity, and Mode Manager
    let nvs_partition = EspDefaultNvsPartition::take()?;
    let mut nvs_device = EspNvs::new(nvs_partition.clone(), "DEVICE", true)?;
    let device_id = device_id::load_or_create(&mut nvs_device)?;
    let nvs_mode = EspNvs::new(nvs_partition.clone(), "STATE", true)?;
    let mut mode_manager = mode::ModeManager::new(nvs_mode)?;
    log::info!("Mode system initialized: {}", mode_manager.current().name());

    // Seed the POMODORO characteristic with an idle status so reads before the
    // first status push are already valid.
    let mut last_pomodoro_status = {
        let nvs_timer = EspNvs::new(nvs_partition.clone(), pomodoro::NVS_NAMESPACE, true)?;
        let (work_min, break_min) = pomodoro::load_durations(&nvs_timer);
        pomodoro::idle_status(work_min, break_min)
    };
    let mut last_timer_status = timer::idle_status();

    // Initialize BLE
    let ble = BluetoothManager::init(
        mode_manager.current() as u8,
        last_pomodoro_status,
        last_timer_status,
        device_id,
    )?;
    log::info!("BLE initialized");
    let mut ble_bonded = ble.has_bonded_peer();
    display.show(disconnected_icon(ble_bonded));

    // Initialize buttons (main = red = GPIO3, sub = white = GPIO4)
    let mut buttons = Buttons::new(peripherals.pins.gpio3.into(), peripherals.pins.gpio4.into())?;
    log::info!("Buttons initialized (red=GPIO3, white=GPIO4)");

    // Create handler for current mode
    let mut handler = handlers::create_handler(mode_manager.current(), nvs_partition.clone())?;
    let mut ble_connected = false;
    let mut client_ready = false;
    let mut connection_icon_on = true;
    let mut last_connection_blink = Instant::now();

    // Main loop
    loop {
        let main_pressed = buttons.red.poll();
        let sub_pressed = buttons.white.poll();

        // Drain BLE commands
        while let Some(cmd) = ble.take_command() {
            match cmd {
                BleCommand::ConnectionChanged(connected) => {
                    ble_connected = connected;
                    client_ready = false;
                    connection_icon_on = true;
                    last_connection_blink = Instant::now();
                    display.show(disconnected_icon(ble_bonded));
                }
                BleCommand::BondChanged(bonded) => {
                    ble_bonded = bonded;
                    if !client_ready {
                        display.show(disconnected_icon(ble_bonded));
                    }
                }
                BleCommand::AuthenticationFailed => {
                    ble_connected = false;
                    client_ready = false;
                    display.show(disconnected_icon(ble_bonded));
                }
                BleCommand::ClientReady => {
                    if ble_connected && !client_ready {
                        client_ready = true;
                        display.show(&handler.on_enter());
                    }
                }
                BleCommand::SwitchMode(m) => {
                    let new_mode = Mode::from_u8(m);
                    if new_mode == mode_manager.current() {
                        continue;
                    }
                    if mode_manager.current() == Mode::Pomodoro {
                        // Leaving Pomodoro mode drops the countdown; report idle.
                        last_pomodoro_status =
                            pomodoro::idle_status(last_pomodoro_status[4], last_pomodoro_status[5]);
                        ble.notify_pomodoro_status(&last_pomodoro_status);
                    } else if mode_manager.current() == Mode::Timer {
                        last_timer_status = timer::idle_status();
                        ble.notify_timer_status(&last_timer_status);
                    }
                    if let Err(e) = mode_manager.switch_to(new_mode) {
                        log::error!("BLE switch_to failed: {:?}", e);
                    }
                    ble.notify_mode_change(mode_manager.current() as u8);
                    handler =
                        handlers::create_handler(mode_manager.current(), nvs_partition.clone())?;
                    if client_ready {
                        display.show(&handler.on_enter());
                    }
                }
                BleCommand::SetDisplayData(data) => {
                    handler.on_ble_data(data);
                }
                BleCommand::SetBrightness(level) => {
                    display.set_intensity(level);
                    ble.notify_brightness_change(level);
                    log::info!("Brightness set to {}", level);
                }
                BleCommand::Pomodoro(timer_cmd) => {
                    handler.on_pomodoro_command(timer_cmd);
                    if mode_manager.current() != Mode::Pomodoro {
                        // Pomodoro commands are ignored outside Pomodoro mode; restore
                        // the status value so READ stays accurate.
                        ble.set_pomodoro_status(&last_pomodoro_status);
                    }
                }
                BleCommand::Timer(timer_cmd) => {
                    handler.on_timer_command(timer_cmd);
                    if mode_manager.current() != Mode::Timer {
                        ble.set_timer_status(&last_timer_status);
                    }
                }
            }
        }

        // Mode-specific button handling
        if main_pressed {
            log::info!("[{}] Main button", mode_manager.current().name());
            handler.on_main_button();
        }
        if sub_pressed {
            log::info!("[{}] Sub button", mode_manager.current().name());
            handler.on_sub_button();
        }

        // Display update
        if let Some(frame) = handler.tick() {
            if client_ready {
                display.show(&frame);
            }
        }

        // Blink the disconnected icon while the BLE link, authentication, and
        // Android initial GATT synchronization are still in progress.
        if ble_connected
            && !client_ready
            && last_connection_blink.elapsed().as_millis() >= CONNECTION_BLINK_MS
        {
            connection_icon_on = !connection_icon_on;
            last_connection_blink = Instant::now();
            if connection_icon_on {
                display.show(disconnected_icon(ble_bonded));
            } else {
                display.show(&assets::PATTERN_ALL_OFF);
            }
        }

        // Pomodoro status: pushed on state/phase changes and once per second
        // while running (Pomodoro mode only).
        if let Some(status) = handler.poll_pomodoro_status() {
            last_pomodoro_status = status;
            ble.notify_pomodoro_status(&status);
        }
        if let Some(status) = handler.poll_timer_status() {
            last_timer_status = status;
            ble.notify_timer_status(&status);
        }

        FreeRtos::delay_ms(50);
    }
}
