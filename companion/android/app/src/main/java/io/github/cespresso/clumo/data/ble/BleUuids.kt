package io.github.cespresso.clumo.data.ble

import java.util.UUID

/** BLE protocol v1 identifiers for the CLumo firmware. */
object BleUuids {
    const val DEVICE_NAME_PREFIX = "CLumo"

    val SERVICE: UUID = UUID.fromString("455aa9f0-2999-43de-81b4-54e0de255927")

    /** WRITE | WRITE_NR: 8 bytes. Display mode: row bitmap. Visualizer mode: 8 column heights 0..8. */
    val DISPLAY: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18585")

    /** READ | WRITE | NOTIFY: u8 mode. */
    val MODE: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18586")

    /** READ | WRITE | NOTIFY: timer commands / 6-byte status. */
    val TIMER: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18587")

    /** READ | WRITE | NOTIFY: u8 0..15. */
    val BRIGHTNESS: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18588")

    /** READ: 16-byte stable device identity. */
    val DEVICE_ID: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18589")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MODE_TIMER = 0
    const val MODE_DISPLAY = 1
    const val MODE_VISUALIZER = 2

    const val TIMER_CMD_START = 0x01
    const val TIMER_CMD_PAUSE = 0x02
    const val TIMER_CMD_RESET = 0x03
    const val TIMER_CMD_SET_DURATIONS = 0x10
}
