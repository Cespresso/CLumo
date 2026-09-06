/// 8x8 LED matrix dot patterns.
/// Each `[u8; 8]` represents 8 rows (row 0 = top), MSB = leftmost pixel.

/// Brand "L" rotated 90 degrees clockwise. Shown while no BLE client is connected.
pub const ICON_DISCONNECTED: [u8; 8] = [0x00, 0x7E, 0x7E, 0x60, 0x60, 0x60, 0x60, 0x00];

/// Disconnected icon with a single mark at the opposite corner for a bonded peer.
pub const ICON_DISCONNECTED_BONDED: [u8; 8] = [0x00, 0x7E, 0x7E, 0x60, 0x60, 0x60, 0x62, 0x00];

// --- Utility ---

pub const PATTERN_ALL_ON: [u8; 8] = [0xFF; 8];
pub const PATTERN_ALL_OFF: [u8; 8] = [0x00; 8];
