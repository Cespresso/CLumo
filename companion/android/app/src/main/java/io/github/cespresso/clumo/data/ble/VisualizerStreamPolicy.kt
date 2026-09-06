package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionState
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
): Boolean = visualizerActive && connectionState != ConnectionState.Ready
