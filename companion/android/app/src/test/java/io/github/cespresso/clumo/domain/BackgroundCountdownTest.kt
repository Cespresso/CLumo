package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundCountdownTest {

    private val runningPomodoro = PomodoroStatus(
        PomodoroStatus.STATE_RUNNING,
        PomodoroStatus.PHASE_WORK,
        15 * 60,
        25,
        5,
    )
    private val runningTimer = CountdownTimerStatus(CountdownTimerStatus.STATE_RUNNING, 120, 5, 0)

    @Test
    fun theModeBeingLookedAtIsNeverInTheBackground() {
        assertEquals(
            emptyList<BackgroundCountdown>(),
            backgroundCountdownsFor(DeviceMode.POMODORO, runningPomodoro, null),
        )
        assertEquals(
            emptyList<BackgroundCountdown>(),
            backgroundCountdownsFor(DeviceMode.TIMER, null, runningTimer),
        )
    }

    @Test
    fun idleIsNotStartedRatherThanRunningElsewhere() {
        assertEquals(
            emptyList<BackgroundCountdown>(),
            backgroundCountdownsFor(
                DeviceMode.DISPLAY,
                PomodoroStatus.DEFAULT,
                CountdownTimerStatus.DEFAULT,
            ),
        )
    }

    @Test
    fun pausedAndCompletedAreReportedAsThemselvesNotAsRunning() {
        assertEquals(
            listOf(BackgroundCountdown.PomodoroPaused),
            backgroundCountdownsFor(
                DeviceMode.DISPLAY,
                runningPomodoro.copy(state = PomodoroStatus.STATE_PAUSED),
                null,
            ),
        )
        assertEquals(
            listOf(BackgroundCountdown.TimerPaused),
            backgroundCountdownsFor(
                DeviceMode.DISPLAY,
                null,
                runningTimer.copy(state = CountdownTimerStatus.STATE_PAUSED),
            ),
        )
        assertEquals(
            listOf(BackgroundCountdown.TimerCompleted),
            backgroundCountdownsFor(
                DeviceMode.DISPLAY,
                null,
                runningTimer.copy(state = CountdownTimerStatus.STATE_COMPLETED, remainingSec = 0),
            ),
        )
    }

    @Test
    fun bothCanBeInTheBackgroundAtOnce() {
        assertEquals(
            listOf(BackgroundCountdown.PomodoroRunning, BackgroundCountdown.TimerRunning),
            backgroundCountdownsFor(DeviceMode.VISUALIZER, runningPomodoro, runningTimer),
        )
    }
}
