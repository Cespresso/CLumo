package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerStreamPolicyTest {
    @Test
    fun allowsVisualizerOnlyWhileReadyInConfirmedVisualizerMode() {
        assertTrue(canRunAudioVisualizer(ConnectionState.Ready, DeviceMode.VISUALIZER))
        assertFalse(canRunAudioVisualizer(ConnectionState.Ready, DeviceMode.DISPLAY))
        assertFalse(canRunAudioVisualizer(ConnectionState.Synchronizing, DeviceMode.VISUALIZER))
        assertFalse(canRunAudioVisualizer(ConnectionState.Ready, null))
    }

    @Test
    fun stopsVisualizerWhenLeavingVisualizerMode() {
        assertTrue(
            shouldStopVisualizerForModeChange(
                visualizerActive = true,
                requestedMode = DeviceMode.DISPLAY,
            ),
        )
        assertFalse(
            shouldStopVisualizerForModeChange(
                visualizerActive = true,
                requestedMode = DeviceMode.VISUALIZER,
            ),
        )
        assertFalse(
            shouldStopVisualizerForModeChange(
                visualizerActive = false,
                requestedMode = DeviceMode.DISPLAY,
            ),
        )
    }

    @Test
    fun stopsVisualizerWhenTheReadyLinkIsLost() {
        assertTrue(shouldStopVisualizerForLinkChange(true, ConnectionState.Reconnecting))
        assertTrue(shouldStopVisualizerForLinkChange(true, ConnectionState.Disconnected))
        assertFalse(shouldStopVisualizerForLinkChange(true, ConnectionState.Ready))
        assertFalse(shouldStopVisualizerForLinkChange(false, ConnectionState.Disconnected))
    }
}
