use esp_idf_hal::sys::EspError;
use esp_idf_svc::nvs::{EspNvs, NvsDefault};

use crate::utils::rng::random_u32;

const NVS_KEY: &str = "DEVICE_ID";

/// Stable 16-byte identifier (UUIDv4) generated on first boot and persisted in NVS.
pub type DeviceId = [u8; 16];

/// Load the device ID from NVS, generating and persisting a new UUIDv4 on first boot.
pub fn load_or_create(nvs: &mut EspNvs<NvsDefault>) -> Result<DeviceId, EspError> {
    let mut buf = [0u8; 16];
    if let Some(bytes) = nvs.get_blob(NVS_KEY, &mut buf)? {
        if bytes.len() == 16 {
            log::info!("Device ID loaded from NVS: {}", format(&buf));
            return Ok(buf);
        }
        log::warn!(
            "Device ID in NVS has unexpected length {}, regenerating",
            bytes.len()
        );
    }

    let id = generate_v4();
    nvs.set_blob(NVS_KEY, &id)?;
    log::info!("Device ID generated and persisted: {}", format(&id));
    Ok(id)
}

fn generate_v4() -> DeviceId {
    let mut bytes = [0u8; 16];
    for chunk in bytes.chunks_mut(4) {
        let r = random_u32().to_le_bytes();
        chunk.copy_from_slice(&r[..chunk.len()]);
    }
    // RFC 4122: version 4
    bytes[6] = (bytes[6] & 0x0F) | 0x40;
    // RFC 4122: variant 10xx
    bytes[8] = (bytes[8] & 0x3F) | 0x80;
    bytes
}

/// Canonical `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` form.
pub fn format(id: &DeviceId) -> String {
    format!(
        "{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        id[0], id[1], id[2], id[3],
        id[4], id[5],
        id[6], id[7],
        id[8], id[9],
        id[10], id[11], id[12], id[13], id[14], id[15],
    )
}

/// 4-char uppercase hex suffix used in the BLE advertising name.
pub fn short(id: &DeviceId) -> String {
    format!("{:02X}{:02X}", id[0], id[1])
}
