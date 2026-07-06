use esp_idf_hal::gpio::{AnyIOPin, Input, PinDriver, Pull};
use esp_idf_hal::sys::EspError;
use std::time::Instant;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum PressType {
    Short,
    Long,
}

pub struct Button<'d> {
    pin: PinDriver<'d, AnyIOPin, Input>,
    last_state: bool,
    press_start: Option<Instant>,
    debounce_ms: u32,
    long_press_ms: u64,
}

impl<'d> Button<'d> {
    pub fn new(pin: AnyIOPin, debounce_ms: u32, long_press_ms: u64) -> Result<Self, EspError> {
        let mut pin_driver = PinDriver::input(pin)?;
        pin_driver.set_pull(Pull::Up)?;

        Ok(Self {
            pin: pin_driver,
            last_state: true, // Pull-up: HIGH when not pressed
            press_start: None,
            debounce_ms,
            long_press_ms,
        })
    }

    /// Poll button state. Returns Some(PressType) on button release.
    pub fn poll(&mut self) -> Option<PressType> {
        let current = self.pin.is_high();

        // Press start (HIGH -> LOW)
        if self.last_state && !current {
            self.press_start = Some(Instant::now());
            self.last_state = current;
            return None;
        }

        // Press end (LOW -> HIGH)
        if !self.last_state && current {
            self.last_state = current;
            if let Some(start) = self.press_start.take() {
                let duration_ms = start.elapsed().as_millis() as u64;
                if duration_ms < self.debounce_ms as u64 {
                    return None;
                }
                if duration_ms >= self.long_press_ms {
                    return Some(PressType::Long);
                } else {
                    return Some(PressType::Short);
                }
            }
        }

        self.last_state = current;
        None
    }
}

pub struct Buttons<'d> {
    pub red: Button<'d>,
    pub white: Button<'d>,
}

impl<'d> Buttons<'d> {
    pub fn new(red_pin: AnyIOPin, white_pin: AnyIOPin) -> Result<Self, EspError> {
        Ok(Self {
            red: Button::new(red_pin, 50, 1000)?,
            white: Button::new(white_pin, 50, 1000)?,
        })
    }
}
