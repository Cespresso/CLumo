use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;
use esp_idf_svc::nvs::{EspDefaultNvsPartition, EspNvs};

use crate::utils::button::{Buttons, PressType};
use crate::utils::led::Display;

mod assets;
mod handlers;
mod mode;
mod utils;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    esp_idf_svc::sys::link_patches();
    esp_idf_svc::log::EspLogger::initialize_default();
    log::info!("=== CLumo Starting ===");

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

    // Initialize NVS and Mode Manager
    let nvs_partition = EspDefaultNvsPartition::take()?;
    let nvs_mode = EspNvs::new(nvs_partition, "CLUMO", true)?;
    let mut mode_manager = mode::ModeManager::new(nvs_mode)?;
    log::info!("Mode system initialized: {}", mode_manager.current().name());

    // Initialize buttons (red=GPIO3, white=GPIO4)
    let mut buttons = Buttons::new(
        peripherals.pins.gpio3.into(),
        peripherals.pins.gpio4.into(),
    )?;
    log::info!("Buttons initialized (red=GPIO3, white=GPIO4)");

    // Create handler for current mode
    let mut handler = handlers::create_handler(mode_manager.current());
    display.show(&handler.on_enter());

    // Main loop
    loop {
        let red_press = buttons.red.poll();
        let white_press = buttons.white.poll();

        // White long-press: cycle mode (universal, consumed before mode dispatch)
        let white_press = if let Some(PressType::Long) = white_press {
            let next = mode_manager.current().next();
            if let Err(e) = mode_manager.switch_to(next) {
                log::error!("Failed to switch mode: {:?}", e);
            }
            handler = handlers::create_handler(mode_manager.current());
            // Mode icon splash, then hand over to the handler
            display.show(&mode_manager.current().icon());
            FreeRtos::delay_ms(500);
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

        FreeRtos::delay_ms(50);
    }
}
