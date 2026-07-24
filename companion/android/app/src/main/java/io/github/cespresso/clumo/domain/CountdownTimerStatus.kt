package io.github.cespresso.clumo.domain

/**
 * Decoded TIMER characteristic status:
 * [state(0 idle / 1 running / 2 paused / 3 completed), remainingSec u16 BE,
 * configuredMin, configuredSec]
 */
data class CountdownTimerStatus(
    val state: Int,
    val remainingSec: Int,
    val configuredMin: Int,
    val configuredSec: Int,
) {
    val isIdle: Boolean get() = state == STATE_IDLE
    val isRunning: Boolean get() = state == STATE_RUNNING
    val isPaused: Boolean get() = state == STATE_PAUSED
    val isCompleted: Boolean get() = state == STATE_COMPLETED
    val configuredTotalSec: Int get() = configuredMin * 60 + configuredSec

    fun formatRemaining(): String = "%02d:%02d".format(remainingSec / 60, remainingSec % 60)

    companion object {
        const val STATE_IDLE = 0
        const val STATE_RUNNING = 1
        const val STATE_PAUSED = 2
        const val STATE_COMPLETED = 3

        val DEFAULT = CountdownTimerStatus(STATE_IDLE, 5 * 60, 5, 0)

        fun parse(value: ByteArray): CountdownTimerStatus? {
            if (value.size < 5) return null
            val state = value[0].toInt() and 0xFF
            val minutes = value[3].toInt() and 0xFF
            val seconds = value[4].toInt() and 0xFF
            if (state !in STATE_IDLE..STATE_COMPLETED) return null
            if (minutes !in 0..59 || seconds !in 0..59) return null
            if (minutes == 0 && seconds == 0) return null
            return CountdownTimerStatus(
                state = state,
                remainingSec = ((value[1].toInt() and 0xFF) shl 8) or
                    (value[2].toInt() and 0xFF),
                configuredMin = minutes,
                configuredSec = seconds,
            )
        }
    }
}
