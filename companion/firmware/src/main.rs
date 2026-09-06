use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs};
use std::time::Instant;

use crate::display_commit_policy::should_auto_commit_display;
use crate::handlers::Runtime;
use crate::mode::Mode;
use crate::utils::bluetooth::{BleCommand, BluetoothManager, BUTTON_MAIN, BUTTON_SUB};
use crate::utils::button::Buttons;
use crate::utils::device_id;
use crate::utils::led::Display;

mod assets;
mod countdown;
mod display_commit_policy;
mod display_state;
mod handlers;
mod mode;
mod mode_values;
mod settings_values;
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
    display.set_intensity(mode_manager.brightness());

    let mut runtime = Runtime::new(nvs_partition.clone())?;

    // Initialize BLE
    let ble = BluetoothManager::init(
        mode_manager.current() as u8,
        runtime.pomodoro_status(),
        runtime.timer_status(),
        mode_manager.brightness(),
        device_id,
    )?;
    log::info!("BLE initialized");
    let mut ble_bonded = ble.has_bonded_peer();
    display.show(disconnected_icon(ble_bonded));

    // main = red, sub = white
    let mut buttons = Buttons::new(peripherals.pins.gpio3.into(), peripherals.pins.gpio4.into())?;
    log::info!("Buttons initialized (red=GPIO3, white=GPIO4)");

    let mut ble_connected = false;
    let mut client_ready = false;
    let mut connection_icon_on = true;
    let mut last_connection_blink = Instant::now();
    let mut explicit_display_commit = false;

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
                    explicit_display_commit = false;
                    connection_icon_on = true;
                    last_connection_blink = Instant::now();
                    if !connected {
                        runtime.cancel_display_preview();
                    }
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
                    explicit_display_commit = false;
                    runtime.cancel_display_preview();
                    display.show(disconnected_icon(ble_bonded));
                }
                BleCommand::ClientReady => {
                    if ble_connected && !client_ready {
                        client_ready = true;
                        display.show(&runtime.on_enter(mode_manager.current()));
                    }
                }
                BleCommand::SwitchMode(m) => {
                    let new_mode = Mode::from_u8(m);
                    if new_mode == mode_manager.current() {
                        if new_mode == Mode::Display {
                            runtime.commit_display_preview();
                            explicit_display_commit = true;
                        }
                        continue;
                    }
                    explicit_display_commit = false;
                    runtime.cancel_display_preview();
                    if let Err(e) = mode_manager.switch_to(new_mode) {
                        log::error!("BLE switch_to failed: {:?}", e);
                    }
                    ble.notify_mode_change(mode_manager.current() as u8);
                    if client_ready {
                        display.show(&runtime.on_enter(mode_manager.current()));
                    }
                }
                BleCommand::SetDisplayData(data) => {
                    let mode = mode_manager.current();
                    runtime.on_display_data(mode, data);
                    if mode == Mode::Display && should_auto_commit_display(explicit_display_commit)
                    {
                        runtime.commit_display_preview();
                    }
                }
                BleCommand::SetBrightness(level) => match mode_manager.set_brightness(level) {
                    Ok(applied) => {
                        display.set_intensity(applied);
                        ble.notify_brightness_change(applied);
                        log::info!("Brightness set to {}", applied);
                    }
                    Err(e) => {
                        log::warn!("Brightness persistence failed: {:?}", e);
                        ble.notify_brightness_change(mode_manager.brightness());
                    }
                },
                BleCommand::Pomodoro(timer_cmd) => {
                    if mode_manager.current() == Mode::Pomodoro {
                        runtime.on_pomodoro_command(timer_cmd);
                    } else {
                        // Pomodoro commands are ignored outside Pomodoro mode; restore
                        // the status value so READ stays accurate.
                        ble.set_pomodoro_status(&runtime.pomodoro_status());
                    }
                }
                BleCommand::Timer(timer_cmd) => {
                    if mode_manager.current() == Mode::Timer {
                        runtime.on_timer_command(timer_cmd);
                    } else {
                        ble.set_timer_status(&runtime.timer_status());
                    }
                }
            }
        }

        // Pomodoro and Timer own their state, so they handle their own presses
        // and keep working while disconnected. Display and Visualizer content
        // lives in the app, so their presses are forwarded and the app decides
        // what they mean.
        // The mode must be read after the BLE command drain so a press is attributed
        // to the currently rendered mode.
        let mode = mode_manager.current();
        for (pressed, is_main) in [(main_pressed, true), (sub_pressed, false)] {
            if !pressed {
                continue;
            }
            log::info!(
                "[{}] {} button",
                mode.name(),
                if is_main { "Main" } else { "Sub" }
            );
            match mode {
                Mode::Pomodoro | Mode::Timer => {
                    runtime.on_button(mode, is_main);
                }
                Mode::Display | Mode::Visualizer => {
                    if client_ready {
                        ble.notify_button(mode, if is_main { BUTTON_MAIN } else { BUTTON_SUB });
                    } else {
                        log::info!("Button press dropped: no synced client");
                    }
                }
            }
        }

        // Display update
        if let Some(frame) = runtime.tick(mode) {
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

        if let Some(status) = runtime.poll_pomodoro_status() {
            ble.notify_pomodoro_status(&status);
        }
        if let Some(status) = runtime.poll_timer_status() {
            ble.notify_timer_status(&status);
        }

        FreeRtos::delay_ms(50);
    }
}
