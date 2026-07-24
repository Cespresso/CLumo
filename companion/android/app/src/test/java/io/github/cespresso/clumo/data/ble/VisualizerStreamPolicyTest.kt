package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerStreamPolicyTest {
    @Test
    fun allowsVisualizerOnlyWhileReadyInConfirmedVisualizerMode() {
        assertTrue(canRunAudioVisualizer(ConnectionState.Ready, BleUuids.MODE_VISUALIZER))
        assertFalse(canRunAudioVisualizer(ConnectionState.Ready, BleUuids.MODE_DISPLAY))
        assertFalse(canRunAudioVisualizer(ConnectionState.Synchronizing, BleUuids.MODE_VISUALIZER))
        assertFalse(canRunAudioVisualizer(ConnectionState.Ready, null))
    }

    @Test
    fun stopsVisualizerWhenLeavingVisualizerMode() {
        assertTrue(
            shouldStopVisualizerForModeChange(
                visualizerActive = true,
                requestedMode = BleUuids.MODE_DISPLAY,
            )
        )
        assertFalse(
            shouldStopVisualizerForModeChange(
                visualizerActive = true,
                requestedMode = BleUuids.MODE_VISUALIZER,
            )
        )
        assertFalse(
            shouldStopVisualizerForModeChange(
                visualizerActive = false,
                requestedMode = BleUuids.MODE_DISPLAY,
            )
        )
    }
}
