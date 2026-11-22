package io.github.cespresso.clumo.data.ble

import java.util.UUID

/** Dedicated v3 service; standard HID remains owned by the Android OS. */
object HeadphoneStandProtocol {
    private fun uuid(suffix: String): UUID = UUID.fromString("d973$suffix-b19e-4c81-a5e8-36fbd47a9c10")
    val SERVICE = uuid("0000")
    val INFO = uuid("0001")
    val DEVICE_ID = uuid("0002")
    val BRIGHTNESS = uuid("0003")
    val DISPLAY_FRAME = uuid("0004")
    val VISUALIZER = uuid("0005")
    val STANDBY = uuid("0006")
    val VOLUME = uuid("0007")
    val DISPLAY_SETTINGS = uuid("0008")
    val CONTROL_BINDINGS = uuid("0009")
    val REQUIRED = setOf(INFO, DEVICE_ID, BRIGHTNESS, DISPLAY_FRAME, VISUALIZER, STANDBY, VOLUME, DISPLAY_SETTINGS, CONTROL_BINDINGS)
    val INITIAL_READS = listOf(DEVICE_ID, BRIGHTNESS, DISPLAY_FRAME, STANDBY, DISPLAY_SETTINGS)
    val NOTIFICATIONS = listOf(BRIGHTNESS, DISPLAY_FRAME, STANDBY, DISPLAY_SETTINGS)

    fun acceptsInfo(value: ByteArray): Boolean = value.contentEquals(byteArrayOf(4, 1))

    fun allowsWrite(uuid: UUID, value: ByteArray): Boolean =
        when (uuid) {
            CONTROL_BINDINGS -> ControlBindings.decode(value) != null
            DISPLAY_SETTINGS -> StandDisplaySettings.decode(value) != null
            BRIGHTNESS -> singleInRange(value, 0..15)
            STANDBY -> singleInRange(value, 0..2)
            VOLUME -> value.contentEquals(byteArrayOf(0)) ||
                (value.size == 2 && value[0] == 1.toByte() && (value[1].toInt() and 0xFF) in 0..100)
            DISPLAY_FRAME -> value.size == 8
            VISUALIZER -> value.size == 8 && value.all { (it.toInt() and 0xFF) in 0..8 }
            else -> false
        }

    /** Explicit unknown or a confirmed percent; old sentinel packets are unsupported. */
    fun encodeVolume(percent: Int?): ByteArray? =
        when {
            percent == null -> byteArrayOf(0)
            percent in 0..100 -> byteArrayOf(1, percent.toByte())
            else -> null
        }

    fun singleInRange(value: ByteArray, range: IntRange): Boolean = value.size == 1 && (value[0].toInt() and 0xFF) in range
}
