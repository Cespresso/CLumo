use embedded_hal::spi::{Mode, Phase, Polarity};
use esp_idf_hal::gpio::{AnyIOPin, AnyOutputPin};
use esp_idf_hal::peripheral::Peripheral;
use esp_idf_hal::prelude::FromValueType;
use esp_idf_hal::spi::config::Config;
use esp_idf_hal::spi::{SpiDeviceDriver, SpiDriver, SpiDriverConfig, SPI2};
use esp_idf_hal::sys::EspError;

/// 8x8 LED matrix display backed by MAX7219 over SPI.
pub struct Display<'d> {
    spi: SpiDeviceDriver<'d, SpiDriver<'d>>,
}

impl<'d> Display<'d> {
    /// Initialize SPI bus, configure MAX7219, and return a ready-to-use display.
    pub fn new(
        spi: impl Peripheral<P = SPI2> + 'd,
        sclk: AnyOutputPin,
        cs: AnyOutputPin,
        mosi: AnyOutputPin,
    ) -> Result<Self, EspError> {
        let spi_drv = SpiDriver::new(
            spi,
            sclk,
            mosi,
            None::<AnyIOPin>,
            &SpiDriverConfig::new(),
        )?;

        let config = Config::new().baudrate(2.MHz().into()).data_mode(Mode {
            polarity: Polarity::IdleLow,
            phase: Phase::CaptureOnFirstTransition,
        });
        let mut spi = SpiDeviceDriver::new(spi_drv, Some(cs), &config)?;

        // Configure every register explicitly rather than relying on the
        // power-on-reset defaults (unreliable, especially out of spec), and
        // exit shutdown LAST so the display turns on already configured and
        // blank instead of flashing whatever the registers powered up with.
        spi.write(&[0x0F, 0x00])?; // Display test off
        spi.write(&[0x09, 0x00])?; // Decode mode: none (matrix mode)
        spi.write(&[0x0B, 0x07])?; // Scan limit: all 8 rows
        spi.write(&[0x0A, 0x0F])?; // Intensity: maximum
        for addr in 1..=8u8 {
            spi.write(&[addr, 0x00])?; // Clear all row registers
        }
        spi.write(&[0x0C, 0x01])?; // Exit shutdown (normal operation)

        Ok(Self { spi })
    }

    /// Set display intensity (brightness). `level` is clamped to 0x00..=0x0F.
    pub fn set_intensity(&mut self, level: u8) {
        let level = level.min(0x0F);
        self.spi.write(&[0x0A, level]).unwrap();
    }

    /// Write 8 bytes of row data to the LED matrix.
    /// Data is rotated 180 degrees to compensate for the upside-down mounted MAX7219.
    pub fn show(&mut self, data: &[u8; 8]) {
        for addr in 1..=8u8 {
            // Rotate 180 degrees: reverse row order and reverse bits in each row
            self.spi
                .write(&[addr, data[(8 - addr) as usize].reverse_bits()])
                .unwrap();
        }
    }
}
