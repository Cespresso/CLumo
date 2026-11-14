//! Adopted XIAO ESP32-C3 wiring, active-high LEDs (bottom to top).
use esp_idf_svc::hal::{
    gpio::{AnyIOPin, AnyOutputPin, IOPin, Input, Output, OutputPin, PinDriver, Pull},
    peripherals::Peripherals,
    prelude::FromValueType,
    spi::{
        config::{Config, MODE_0},
        SpiDeviceDriver, SpiDriver, SpiDriverConfig,
    },
};
use esp_idf_svc::sys::EspError;
type Out = PinDriver<'static, AnyOutputPin, Output>;
type In = PinDriver<'static, AnyIOPin, Input>;
pub struct Hardware {
    spi: SpiDeviceDriver<'static, SpiDriver<'static>>,
    volume_leds: [Out; 3],
    buttons: [In; 2],
    knob_button: In,
    encoder: [In; 2],
}
impl Hardware {
    pub fn new() -> Result<(Self, esp_idf_svc::hal::modem::Modem), EspError> {
        let p = Peripherals::take()?;
        // Power indicator is wired to the supply, not controlled by firmware.
        // Volume LEDs source current: GPIO -> resistor -> LED -> GND.
        let driver = SpiDriver::new(
            p.spi2,
            p.pins.gpio8,
            p.pins.gpio2,
            None::<AnyIOPin>,
            &SpiDriverConfig::new(),
        )?;
        let config = Config::new().baudrate(2.MHz().into()).data_mode(MODE_0);
        let spi = SpiDeviceDriver::new(driver, Some(p.pins.gpio9), &config)?;
        let mut hw = Self {
            spi,
            volume_leds: [
                PinDriver::output(p.pins.gpio21.downgrade_output())?,
                PinDriver::output(p.pins.gpio20.downgrade_output())?,
                PinDriver::output(p.pins.gpio10.downgrade_output())?,
            ],
            buttons: [
                PinDriver::input(p.pins.gpio3.downgrade())?,
                PinDriver::input(p.pins.gpio4.downgrade())?,
            ],
            knob_button: PinDriver::input(p.pins.gpio5.downgrade())?,
            encoder: [
                PinDriver::input(p.pins.gpio6.downgrade())?,
                PinDriver::input(p.pins.gpio7.downgrade())?,
            ],
        };
        for pin in hw.buttons.iter_mut() {
            pin.set_pull(Pull::Up)?;
        }
        {
            hw.knob_button.set_pull(Pull::Up)?;
            for pin in hw.encoder.iter_mut() {
                pin.set_pull(Pull::Up)?;
            }
        }
        log::info!("MATRIX transport: hardware SPI MODE0 2MHz; DIN=D0 CLK=D8 CS=D9");
        hw.volume(0)?;
        for (register, value) in [(0x0f, 0), (0x0c, 0), (0x09, 0), (0x0b, 7), (0x0a, 4)] {
            hw.register(register, value)?;
        }
        hw.frame([0; 8])?;
        hw.register(0x0c, 1)?;
        Ok((hw, p.modem))
    }
    fn register(&mut self, address: u8, value: u8) -> Result<(), EspError> {
        self.spi.write(&[address, value])
    }
    pub fn brightness(&mut self, value: u8) -> Result<(), EspError> {
        self.register(0x0a, value)
    }
    pub fn frame(&mut self, rows: [u8; 8]) -> Result<(), EspError> {
        let rows = headphone_stand::mounted_frame(rows);
        for (i, row) in rows.iter().enumerate() {
            self.register(i as u8 + 1, *row)?;
        }
        Ok(())
    }
    pub fn led_mask(&mut self, mask: u8) -> Result<(), EspError> {
        for (i, led) in self.volume_leds.iter_mut().enumerate() {
            if mask & (1 << i) != 0 {
                led.set_high()?;
            } else {
                led.set_low()?;
            }
        }
        Ok(())
    }
    pub fn volume(&mut self, count: u8) -> Result<(), EspError> {
        self.led_mask((1u8 << count.min(3)) - 1)
    }
    pub fn buttons(&self) -> [bool; 3] {
        let knob = self.knob_button.is_low();
        [self.buttons[0].is_low(), self.buttons[1].is_low(), knob]
    }
    pub fn encoder(&self) -> u8 {
        u8::from(self.encoder[0].is_high()) * 2 + u8::from(self.encoder[1].is_high())
    }
}
