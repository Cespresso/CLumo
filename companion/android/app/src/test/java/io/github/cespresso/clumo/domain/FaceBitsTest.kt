package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceBitsTest {
    @Test
    fun rendersPomodoroProgress() {
        assertEquals(FaceBits.POMODORO_IDLE, FaceBits.fromPomodoro(PomodoroStatus.DEFAULT))
        assertEquals(
            -1L,
            FaceBits.fromPomodoro(PomodoroStatus.DEFAULT.copy(state = PomodoroStatus.STATE_RUNNING)),
        )
        assertEquals(
            -1L shl 32,
            FaceBits.fromPomodoro(
                PomodoroStatus(
                    state = PomodoroStatus.STATE_RUNNING,
                    phase = PomodoroStatus.PHASE_WORK,
                    remainingSec = 15 * 60,
                    workMin = 30,
                    breakMin = 5,
                ),
            ),
        )
        assertEquals(
            Long.MIN_VALUE,
            FaceBits.fromPomodoro(
                PomodoroStatus(
                    state = PomodoroStatus.STATE_RUNNING,
                    phase = PomodoroStatus.PHASE_WORK,
                    remainingSec = 1,
                    workMin = 30,
                    breakMin = 5,
                ),
            ),
        )
        assertEquals(
            FaceBits.EMPTY,
            FaceBits.fromPomodoro(
                PomodoroStatus(
                    state = PomodoroStatus.STATE_RUNNING,
                    phase = PomodoroStatus.PHASE_WORK,
                    remainingSec = 0,
                    workMin = 30,
                    breakMin = 5,
                ),
            ),
        )
    }

    @Test
    fun rendersCountdownTimerProgress() {
        assertEquals(FaceBits.TIMER_IDLE, FaceBits.fromCountdownTimer(CountdownTimerStatus.DEFAULT))
        assertEquals(
            -1L,
            FaceBits.fromCountdownTimer(CountdownTimerStatus.DEFAULT.copy(state = CountdownTimerStatus.STATE_RUNNING)),
        )
        assertEquals(
            -1L shl 32,
            FaceBits.fromCountdownTimer(
                CountdownTimerStatus(
                    state = CountdownTimerStatus.STATE_RUNNING,
                    remainingSec = 150,
                    configuredMin = 5,
                    configuredSec = 0,
                ),
            ),
        )
        assertEquals(
            Long.MIN_VALUE,
            FaceBits.fromCountdownTimer(
                CountdownTimerStatus(
                    state = CountdownTimerStatus.STATE_RUNNING,
                    remainingSec = 1,
                    configuredMin = 5,
                    configuredSec = 0,
                ),
            ),
        )
        assertEquals(
            FaceBits.EMPTY,
            FaceBits.fromCountdownTimer(
                CountdownTimerStatus(
                    state = CountdownTimerStatus.STATE_COMPLETED,
                    remainingSec = 0,
                    configuredMin = 5,
                    configuredSec = 0,
                ),
            ),
        )
    }
}
