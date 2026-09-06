package io.github.cespresso.clumo.domain

/**
 * A Pomodoro or Timer still counting down, or waiting to be acknowledged, while the device
 * shows some other mode. CLumo keeps both alive across a mode change, and the controls for
 * them are mode-gated, so one left running elsewhere is otherwise invisible and unreachable.
 */
enum class BackgroundCountdown {
    PomodoroRunning,
    PomodoroPaused,
    TimerRunning,
    TimerPaused,
    TimerCompleted,
}

/**
 * Idle is not "in the background", it is simply not started; every other state is something
 * the user would want to see and be able to stop from wherever they are.
 */
fun backgroundCountdownsFor(
    mode: Int?,
    pomodoro: PomodoroStatus?,
    timer: CountdownTimerStatus?,
): List<BackgroundCountdown> =
    buildList {
        if (mode != DeviceMode.POMODORO && pomodoro != null && !pomodoro.isIdle) {
            add(
                if (pomodoro.isRunning) {
                    BackgroundCountdown.PomodoroRunning
                } else {
                    BackgroundCountdown.PomodoroPaused
                },
            )
        }
        if (mode != DeviceMode.TIMER && timer != null && !timer.isIdle) {
            add(
                when {
                    timer.isRunning -> BackgroundCountdown.TimerRunning
                    timer.isCompleted -> BackgroundCountdown.TimerCompleted
                    else -> BackgroundCountdown.TimerPaused
                },
            )
        }
    }
