package io.github.cespresso.clumo.ui.components.product

import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAppearance

/** Same observed presentation as the 2D device; no independent BLE or animation source. */
data class ProductDisplayState(
    val appearance: DeviceAppearance = DeviceAppearance.DEFAULT,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val bits: Long = 0L,
    val brightnessLevel: Int = Brightness.MAX_LEVEL,
    val volumeSegments: Int = 0,
    val outlinedRoles: List<String> = emptyList(),
) {
    internal fun regionColors(): Map<String, Int> =
        mapOf(
            "enclosure" to appearance.enclosureColor.value,
            "button_a" to appearance.buttonAColor.value,
            "button_b" to appearance.standColor("button_b").value,
            "stand_clip" to appearance.standColor("stand_clip").value,
            "front_holder" to appearance.standColor("front_holder").value,
            "rear_holder" to appearance.standColor("rear_holder").value,
            "front_insert" to appearance.standColor("front_insert").value,
            "stand_logo" to appearance.standColor("stand_logo").value,
            "stand_left" to appearance.buttonAColor.value,
            "stand_right" to appearance.buttonAColor.value,
            "stand_knob" to appearance.buttonBColor.value,
            "panel" to appearance.panelColor.value,
            "matrix_surface" to appearance.matrixSurfaceColor.value,
            "window" to appearance.windowColor.value,
        )

    /** One texel per matrix cell, with transmitted intensity in alpha. */
    internal fun ledRgba(rotate180: Boolean = false): ByteArray {
        // The intensity register uses odd duty steps, from 1/32 to 31/32.
        val duty = 2 * brightnessLevel.coerceIn(0, Brightness.MAX_LEVEL) + 1
        val intensity = (255 * duty + 15) / 31
        val liveBits = if (connectionState == ConnectionState.Ready) bits else 0L
        return ByteArray(64 * 4).also { pixels ->
            repeat(64) { cell ->
                val sourceCell = if (rotate180) 63 - cell else cell
                val on = liveBits and (1L shl sourceCell) != 0L
                for (channel in 0..2) {
                    val shift = (2 - channel) * 8
                    pixels[cell * 4 + channel] = (appearance.ledColor.value shr shift).toByte()
                }
                pixels[cell * 4 + 3] = if (on) intensity.toByte() else 0
            }
        }
    }
}
