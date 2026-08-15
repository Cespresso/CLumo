package io.github.cespresso.clumo.ui.components

import androidx.compose.ui.graphics.Color
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.RgbColor

enum class ConnectionRing { None, Pulse, Error }

enum class DeviceVisualLayer(val zIndex: Float) {
    ConnectionRing(0f),
    PhysicalDevice(1f),
}

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
