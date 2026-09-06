package io.github.cespresso.clumo.domain

/** State last confirmed by a CLumo read or notification. */
data class DeviceSnapshot(
    val mode: Int?,
    val brightnessLevel: Int?,
    val pomodoro: PomodoroStatus = PomodoroStatus.DEFAULT,
    val timer: CountdownTimerStatus = CountdownTimerStatus.DEFAULT,
    val committedFrame: Long? = null,
)
