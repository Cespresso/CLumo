package io.github.cespresso.clumo.domain

/**
 * Decoded POMODORO characteristic status:
 * [state(0 idle / 1 running / 2 paused), phase(0 work / 1 break), remainingSec u16 BE, workMin, breakMin]
 */
data class PomodoroStatus(
    val state: Int,
    val phase: Int,
    val remainingSec: Int,
    val workMin: Int,
    val breakMin: Int,
) {
    val isRunning: Boolean get() = state == STATE_RUNNING
    val isIdle: Boolean get() = state == STATE_IDLE
    val isWorkPhase: Boolean get() = phase == PHASE_WORK

    /** Total seconds of the current phase, used by the pixel-countdown mirror. */
    val phaseTotalSec: Int get() = (if (isWorkPhase) workMin else breakMin) * 60

    companion object {
        const val STATE_IDLE = 0
        const val STATE_RUNNING = 1
        const val STATE_PAUSED = 2
        const val PHASE_WORK = 0
        const val PHASE_BREAK = 1

        val DEFAULT = PomodoroStatus(STATE_IDLE, PHASE_WORK, 25 * 60, 25, 5)

        fun parse(value: ByteArray): PomodoroStatus? {
            if (value.size < 6) return null
            return PomodoroStatus(
                state = value[0].toInt() and 0xFF,
                phase = value[1].toInt() and 0xFF,
                remainingSec = ((value[2].toInt() and 0xFF) shl 8) or (value[3].toInt() and 0xFF),
                workMin = value[4].toInt() and 0xFF,
                breakMin = value[5].toInt() and 0xFF,
            )
        }
    }
}
