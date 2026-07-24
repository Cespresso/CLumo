package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionState

internal fun canRunAudioVisualizer(
    connectionState: ConnectionState,
    currentMode: Int?,
): Boolean = connectionState == ConnectionState.Ready &&
    currentMode == BleUuids.MODE_VISUALIZER

internal fun shouldStopVisualizerForModeChange(
    visualizerActive: Boolean,
    requestedMode: Int,
): Boolean = visualizerActive &&
    requestedMode != BleUuids.MODE_VISUALIZER
