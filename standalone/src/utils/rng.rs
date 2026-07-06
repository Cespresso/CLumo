/// RNG utilities based on the ESP32-C3 `esp_random()` API.
/// Without an active RF subsystem the entropy is weaker than true random,
/// which is still plenty for dice rolls and animation timing.

/// Generate a random u32 using the hardware RNG.
pub fn random_u32() -> u32 {
    unsafe { esp_idf_svc::sys::esp_random() }
}

/// Generate a random u32 in the range [min, max).
/// Returns `min` if `min >= max`.
pub fn random_range(min: u32, max: u32) -> u32 {
    if min >= max {
        return min;
    }
    min + random_u32() % (max - min)
}
