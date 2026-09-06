/// Hardware RNG utilities using ESP32-C3's true random number generator.
/// When Wi-Fi or Bluetooth is enabled, `esp_random()` produces true random numbers.

/// Generate a random u32 using the hardware RNG.
pub fn random_u32() -> u32 {
    unsafe { esp_idf_svc::sys::esp_random() }
}
