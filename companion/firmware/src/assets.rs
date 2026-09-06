/// 8x8 LED matrix dot patterns.
/// Each `[u8; 8]` represents 8 rows (row 0 = top), MSB = leftmost pixel.

/// Brand "L" rotated 90 degrees clockwise. Shown while no BLE client is connected.
pub const ICON_DISCONNECTED: [u8; 8] = [0x00, 0x7E, 0x7E, 0x60, 0x60, 0x60, 0x60, 0x00];

/// Disconnected icon with a single mark at the opposite corner for a bonded peer.
pub const ICON_DISCONNECTED_BONDED: [u8; 8] = [0x00, 0x7E, 0x7E, 0x60, 0x60, 0x60, 0x62, 0x00];

/// An hourglass. What Pomodoro shows while idle.
pub const ICON_POMODORO: [u8; 8] = [0x7E, 0x42, 0x24, 0x18, 0x18, 0x24, 0x42, 0x7E];

/// A clock face with the hands at three o'clock. What Timer shows while idle.
pub const ICON_TIMER: [u8; 8] = [0x3C, 0x52, 0x91, 0x9D, 0x81, 0x81, 0x42, 0x3C];

// --- Utility ---

pub const PATTERN_ALL_ON: [u8; 8] = [0xFF; 8];
pub const PATTERN_ALL_OFF: [u8; 8] = [0x00; 8];
