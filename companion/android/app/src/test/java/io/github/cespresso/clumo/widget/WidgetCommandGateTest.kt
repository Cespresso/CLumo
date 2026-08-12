package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCommandGateTest {

    @Test
    fun aReadyLinkExecutesImmediately() {
        assertEquals(
            GateDecision.Execute,
            WidgetCommandGate.decide(
                command = WidgetCommand.TogglePomodoro,
                connectionState = ConnectionState.Ready,
                lastFailureRealtime = null,
                nowRealtime = 1_000L,
            ),
        )
    }

    @Test
    fun aDownLinkConnectsFirst() {
        assertEquals(
            GateDecision.ConnectThenExecute,
            WidgetCommandGate.decide(
                command = WidgetCommand.TogglePomodoro,
                connectionState = ConnectionState.Disconnected,
                lastFailureRealtime = null,
                nowRealtime = 1_000L,
            ),
        )
    }

    @Test
    fun aRecentFailureRefusesAnotherAttempt() {
        assertEquals(
            GateDecision.Refuse,
            WidgetCommandGate.decide(
                command = WidgetCommand.TogglePomodoro,
                connectionState = ConnectionState.Error,
                lastFailureRealtime = 1_000L,
                nowRealtime = 1_000L + WidgetCommandGate.COOLDOWN_MS - 1L,
            ),
        )
    }

    @Test
    fun theCooldownExpires() {
        assertEquals(
            GateDecision.ConnectThenExecute,
            WidgetCommandGate.decide(
                command = WidgetCommand.TogglePomodoro,
                connectionState = ConnectionState.Error,
                lastFailureRealtime = 1_000L,
                nowRealtime = 1_000L + WidgetCommandGate.COOLDOWN_MS,
            ),
        )
    }

    @Test
    fun explicitRetryIgnoresTheCooldown() {
        assertEquals(
            GateDecision.ConnectThenExecute,
            WidgetCommandGate.decide(
                command = WidgetCommand.Retry,
                connectionState = ConnectionState.Error,
                lastFailureRealtime = 1_000L,
                nowRealtime = 1_001L,
            ),
        )
    }

    @Test
    fun aBackwardsClockDoesNotTrapTheUserInCooldown() {
        // elapsedRealtime resets on reboot; a failure "in the future" must not block.
        assertEquals(
            GateDecision.ConnectThenExecute,
            WidgetCommandGate.decide(
                command = WidgetCommand.TogglePomodoro,
                connectionState = ConnectionState.Disconnected,
                lastFailureRealtime = 9_999_000L,
                nowRealtime = 4_000L,
            ),
        )
    }

    @Test
    fun waitingTimesOut() {
        assertFalse(WidgetCommandGate.hasTimedOut(1_000L, 1_000L))
        assertFalse(
            WidgetCommandGate.hasTimedOut(
                1_000L,
                1_000L + WidgetCommandGate.CONNECT_TIMEOUT_MS - 1L,
            ),
        )
        assertTrue(
            WidgetCommandGate.hasTimedOut(
                1_000L,
                1_000L + WidgetCommandGate.CONNECT_TIMEOUT_MS,
            ),
        )
    }
}
