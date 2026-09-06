package io.github.cespresso.clumo.ui.components

import androidx.compose.ui.graphics.Color
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.RgbColor
import kotlin.math.pow

enum class ContentTone { Dark, Light }

enum class ConnectionRing { None, Pulse, Error }

enum class DeviceVisualLayer(val zIndex: Float) {
    ConnectionRing(0f),
    PhysicalDevice(1f),
}

fun contentToneFor(color: RgbColor): ContentTone =
    if (relativeLuminance(color) > 0.179f) ContentTone.Dark else ContentTone.Light

fun connectionRingFor(state: ConnectionState): ConnectionRing = when (state) {
    ConnectionState.Connecting,
    ConnectionState.Reconnecting,
    ConnectionState.Bonding,
    ConnectionState.Connected,
    ConnectionState.Synchronizing,
    -> ConnectionRing.Pulse

    ConnectionState.Error -> ConnectionRing.Error
    ConnectionState.Disconnected,
    ConnectionState.Ready,
    -> ConnectionRing.None
}

fun RgbColor.toComposeColor(): Color = Color(0xFF000000L or value.toLong())

private fun relativeLuminance(color: RgbColor): Float {
    fun linear(channel: Int): Float {
        val normalized = channel / 255f
        return if (normalized <= 0.04045f) {
            normalized / 12.92f
        } else {
            ((normalized + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    val red = linear((color.value shr 16) and 0xFF)
    val green = linear((color.value shr 8) and 0xFF)
    val blue = linear(color.value and 0xFF)
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
