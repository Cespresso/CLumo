package io.github.cespresso.clumo.ui.components

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceRenderingTest {

    @Test
    fun lightColorsUseDarkContent() {
        assertEquals(ContentTone.Dark, contentToneFor(color("#FFFFFF")))
        assertEquals(ContentTone.Dark, contentToneFor(color("#FFF4DC")))
    }

    @Test
    fun darkColorsUseLightContent() {
        assertEquals(ContentTone.Light, contentToneFor(color("#000000")))
        assertEquals(ContentTone.Light, contentToneFor(color("#111111")))
    }

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

    private fun color(hex: String): RgbColor = requireNotNull(RgbColor.parseOrNull(hex))
}
