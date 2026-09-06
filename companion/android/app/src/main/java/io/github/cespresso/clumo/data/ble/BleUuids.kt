package io.github.cespresso.clumo.data.ble

import java.util.UUID

internal enum class GattCompatibilityAction {
    ACCEPT,
    REFRESH_CACHE,
    REJECT,
}

/**
 * A missing [optional] characteristic is indistinguishable from a GATT cache written
 * before the firmware gained it, so refresh once before believing it is really absent.
 * Without this, a device bonded against older firmware would keep its stale cache
 * forever and the features behind the newer characteristic would never come alive.
 */
internal fun gattCompatibilityAction(
    discovered: Set<UUID>,
    required: Set<UUID>,
    optional: Set<UUID>,
    cacheRefreshAttempted: Boolean,
): GattCompatibilityAction = when {
    !discovered.containsAll(required) ->
        if (cacheRefreshAttempted) {
            GattCompatibilityAction.REJECT
        } else {
            GattCompatibilityAction.REFRESH_CACHE
        }
    !discovered.containsAll(optional) && !cacheRefreshAttempted ->
        GattCompatibilityAction.REFRESH_CACHE
    else -> GattCompatibilityAction.ACCEPT
}

/** BLE protocol v2 identifiers for the CLumo firmware. */
object BleUuids {
    const val DEVICE_NAME_PREFIX = "CLumo"

    val SERVICE: UUID = UUID.fromString("455aa9f0-2999-43de-81b4-54e0de255927")

    /** WRITE | WRITE_NR: 8 bytes. Display mode: row bitmap. Visualizer mode: 8 column heights 0..8. */
    val DISPLAY: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18585")

    /** READ | WRITE | NOTIFY: u8 mode. */
    val MODE: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18586")

    /** READ | WRITE | NOTIFY: pomodoro commands / 6-byte status. */
    val POMODORO: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18587")

    /** READ | WRITE | NOTIFY: countdown timer commands / 5-byte status. */
    val TIMER: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858a")

    /** READ | WRITE | NOTIFY: u8 0..15. */
    val BRIGHTNESS: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18588")

    /** READ: 16-byte stable device identity. */
    val DEVICE_ID: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18589")

    /** NOTIFY: 2 bytes [mode, button] for presses the firmware does not handle itself. */
    val BUTTON: UUID = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858b")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MODE_POMODORO = 0
    const val MODE_TIMER = 1
    const val MODE_DISPLAY = 2
    const val MODE_VISUALIZER = 3
    val MODE_ORDER = listOf(MODE_POMODORO, MODE_TIMER, MODE_DISPLAY, MODE_VISUALIZER)

    const val BUTTON_MAIN = 0
    const val BUTTON_SUB = 1

    const val POMODORO_CMD_START = 0x01
    const val POMODORO_CMD_PAUSE = 0x02
    const val POMODORO_CMD_RESET = 0x03
    const val POMODORO_CMD_SET_DURATIONS = 0x10

    const val TIMER_CMD_START = 0x01
    const val TIMER_CMD_PAUSE = 0x02
    const val TIMER_CMD_CANCEL = 0x03
    const val TIMER_CMD_SET_DURATION = 0x10

    fun timerSetDurationPayloadOrNull(minutes: Int, seconds: Int): ByteArray? {
        if (minutes !in 0..59 || seconds !in 0..59) return null
        if (minutes == 0 && seconds == 0) return null
        return byteArrayOf(TIMER_CMD_SET_DURATION.toByte(), minutes.toByte(), seconds.toByte())
    }
}
