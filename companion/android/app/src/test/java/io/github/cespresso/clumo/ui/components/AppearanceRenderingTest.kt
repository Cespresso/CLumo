package io.github.cespresso.clumo.ui.components

import io.github.cespresso.clumo.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceRenderingTest {

    @Test
    fun transientConnectionStatesUseAPulsingOuterRing() {
        listOf(
            ConnectionState.Connecting,
            ConnectionState.Reconnecting,
            ConnectionState.Bonding,
            ConnectionState.Connected,
            ConnectionState.Synchronizing,
        ).forEach { state ->
            assertEquals(ConnectionRing.Pulse, connectionRingFor(state))
        }
    }

    @Test
    fun errorUsesErrorRingWhileReadyAndDisconnectedUseNoRing() {
        assertEquals(ConnectionRing.Error, connectionRingFor(ConnectionState.Error))
        assertEquals(ConnectionRing.None, connectionRingFor(ConnectionState.Ready))
        assertEquals(ConnectionRing.None, connectionRingFor(ConnectionState.Disconnected))
    }

    @Test
    fun connectionRingIsPaintedBehindThePhysicalDevice() {
        assertTrue(
            DeviceVisualLayer.ConnectionRing.zIndex < DeviceVisualLayer.PhysicalDevice.zIndex,
        )
    }
}
