package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSessionStateTest {

    @Test
    fun pendingCommandWinsUntilDeviceReportsCanonicalState() {
        val state = DeviceSessionState(
            link = ConnectionState.Ready,
            observed = DeviceSnapshot(
                mode = DeviceMode.POMODORO,
                brightnessLevel = 4,
            ),
            pending = PendingCommands(
                mode = PendingCommand(DeviceMode.DISPLAY, sentAtRealtime = 1_000),
                brightnessLevel = PendingCommand(12, sentAtRealtime = 1_000),
            ),
        )

        assertEquals(DeviceMode.DISPLAY, state.effectiveMode)
        assertEquals(12, state.effectiveBrightnessLevel)
    }

    @Test
    fun observedStateIsEffectiveWhenThereIsNoPendingCommand() {
        val state = DeviceSessionState(
            link = ConnectionState.Ready,
            observed = DeviceSnapshot(
                mode = DeviceMode.TIMER,
                brightnessLevel = 7,
            ),
        )

        assertEquals(DeviceMode.TIMER, state.effectiveMode)
        assertEquals(7, state.effectiveBrightnessLevel)
    }

    @Test
    fun pendingCommandsExpireAtTheTtlBoundary() {
        val pending = PendingCommands(
            mode = PendingCommand(DeviceMode.VISUALIZER, sentAtRealtime = 2_000),
            brightnessLevel = PendingCommand(3, sentAtRealtime = 2_001),
        )

        val expired = pending.expire(nowRealtime = 5_000, ttlMs = 3_000)

        assertNull(expired.mode)
        assertEquals(3, expired.brightnessLevel?.value)
    }

    @Test
    fun unknownModeFallsBackToPomodoroButUnknownBrightnessStaysUnknown() {
        val state = DeviceSessionState(link = ConnectionState.Synchronizing)

        assertEquals(DeviceMode.POMODORO, state.effectiveMode)
        assertNull(state.effectiveBrightnessLevel)
    }
}
