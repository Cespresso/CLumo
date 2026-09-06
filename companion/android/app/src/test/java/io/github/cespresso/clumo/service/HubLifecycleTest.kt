package io.github.cespresso.clumo.service

import io.github.cespresso.clumo.domain.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubLifecycleTest {

    @Test
    fun noSessionsMeansNoWork() {
        assertFalse(hubHasWorkFor(emptyList()))
    }

    @Test
    fun aDeviceThatWentDarkIsNotWork() {
        // What a registered session looks like once its device is off: still in the map,
        // no longer anything to protect.
        assertFalse(hubHasWorkFor(listOf(ConnectionState.Disconnected)))
        assertFalse(hubHasWorkFor(listOf(ConnectionState.Error)))
        assertFalse(hubHasWorkFor(listOf(ConnectionState.Disconnected, ConnectionState.Error)))
    }

    @Test
    fun anyAttemptOrLiveLinkIsWork() {
        val live = ConnectionState.entries - ConnectionState.Disconnected - ConnectionState.Error
        live.forEach { link ->
            assertTrue(link.name, hubHasWorkFor(listOf(ConnectionState.Disconnected, link)))
        }
    }
}
