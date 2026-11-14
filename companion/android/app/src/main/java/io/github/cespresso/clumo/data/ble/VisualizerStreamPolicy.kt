package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.DeviceMode

internal fun canRunAudioVisualizer(
    connectionState: ConnectionState,
    currentMode: Int?,
): Boolean =
    connectionState == ConnectionState.Ready &&
        currentMode == DeviceMode.VISUALIZER

internal fun shouldStopVisualizerForModeChange(
    visualizerActive: Boolean,
    requestedMode: Int,
): Boolean =
    visualizerActive &&
        requestedMode != DeviceMode.VISUALIZER

internal fun shouldStopVisualizerForLinkChange(
    visualizerActive: Boolean,
    connectionState: ConnectionState,
    kind: DeviceKind = DeviceKind.CLUMO,
): Boolean =
    visualizerActive && when (connectionState) {
        ConnectionState.Ready -> false
        ConnectionState.Disconnected, ConnectionState.Error -> true
        else -> kind != DeviceKind.HEADPHONE_STAND
    }
