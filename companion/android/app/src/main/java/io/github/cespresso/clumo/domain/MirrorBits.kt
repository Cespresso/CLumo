package io.github.cespresso.clumo.domain

/**
 * What the LED matrix is showing, for a mode and the statuses that go with it.
 * Pomodoro/Timer -> pixel countdown; Display -> selected pattern; Visualizer -> column bars.
 *
 * The blink phase is a parameter rather than a clock so the whole mirror stays a value: two
 * places draw it and neither should be deciding when the face is dark.
 */
fun mirrorBitsFor(
    mode: Int?,
    pomodoro: PomodoroStatus?,
    timer: CountdownTimerStatus?,
    selectedPatternBits: String?,
    committedFrame: Long?,
    columns: IntArray,
    visualizerActive: Boolean,
    timerBlinkOn: Boolean,
): Long = when (mode) {
    DeviceMode.POMODORO -> pomodoro?.let { FaceBits.fromPomodoro(it) } ?: FaceBits.EMPTY
    DeviceMode.TIMER -> timer?.let {
        when {
            // A finished timer blinks the whole face rather than showing an empty one.
            it.isCompleted && !timerBlinkOn -> FaceBits.EMPTY
            it.isCompleted -> -1L
            else -> FaceBits.fromCountdownTimer(it)
        }
    } ?: FaceBits.EMPTY

    // CLumo's own frame outranks the app's locally selected pattern: it is still correct
    // after a reinstall, another phone's edit, or a physical-button cycle the app has not
    // caught up with yet.
    DeviceMode.DISPLAY -> committedFrame
        ?: selectedPatternBits?.let { FaceBits.fromBitsString(it) }
        ?: FaceBits.EMPTY
    DeviceMode.VISUALIZER -> if (visualizerActive) FaceBits.fromColumns(columns) else FaceBits.EMPTY
    else -> FaceBits.EMPTY
}
