package io.github.cespresso.clumo.domain

/** 8x8 face bitmask helpers (bit i = row-major cell i, row 0 = top, bit set = lit). */
object FaceBits {
    const val EMPTY = 0L

    fun fromBitsString(bits: String): Long {
        var mask = 0L
        val n = minOf(bits.length, 64)
        for (i in 0 until n) {
            if (bits[i] == '1') mask = mask or (1L shl i)
        }
        return mask
    }

    /** The hourglass CLumo shows while Pomodoro is idle. Same bytes as the firmware's ICON_POMODORO. */
    val POMODORO_IDLE: Long = fromRowBytes(byteArrayOf(0x7E, 0x42, 0x24, 0x18, 0x18, 0x24, 0x42, 0x7E))

    /** The clock face CLumo shows while Timer is idle. Same bytes as the firmware's ICON_TIMER. */
    val TIMER_IDLE: Long = fromRowBytes(byteArrayOf(0x3C, 0x52, 0x91.toByte(), 0x9D.toByte(), 0x81.toByte(), 0x81.toByte(), 0x42, 0x3C))

    /**
     * Pixel countdown, identical rule to the firmware:
     * lit = ceil(remainingSec * 64 / phaseTotalSec) clamped 0..64,
     * with remaining pixels occupying the row-major suffix so they turn off
     * from the top-left toward the bottom-right. Idle shows the mode's icon.
     */
    fun fromPomodoro(status: PomodoroStatus): Long = if (status.isIdle) POMODORO_IDLE else fromProgress(status.remainingSec, status.phaseTotalSec)

    fun fromCountdownTimer(status: CountdownTimerStatus): Long = if (status.isIdle) TIMER_IDLE else fromProgress(status.remainingSec, status.configuredTotalSec)

    private fun fromProgress(remainingSec: Int, total: Int): Long {
        if (total <= 0) return EMPTY
        val lit = ((remainingSec.toLong() * 64 + total - 1) / total)
            .coerceIn(0, 64)
            .toInt()
        return when {
            lit <= 0 -> EMPTY
            lit >= 64 -> -1L
            else -> -1L shl (64 - lit)
        }
    }

    fun toBitsString(mask: Long): String =
        buildString {
            for (i in 0 until 64) append(if ((mask shr i) and 1L == 1L) '1' else '0')
        }

    /** Row bitmap bytes as read from the DISPLAY characteristic, MSB = left column. */
    fun fromRowBytes(bytes: ByteArray): Long {
        var mask = 0L
        for (row in 0 until 8) {
            val b = bytes.getOrElse(row) { 0 }.toInt() and 0xFF
            for (col in 0 until 8) {
                if ((b shr (7 - col)) and 1 == 1) mask = mask or (1L shl (row * 8 + col))
            }
        }
        return mask
    }

    /** Column heights 0..8 -> bars growing from the bottom. */
    fun fromColumns(columns: IntArray): Long {
        var mask = 0L
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val h = columns.getOrElse(col) { 0 }.coerceIn(0, 8)
                if (8 - row <= h) mask = mask or (1L shl (row * 8 + col))
            }
        }
        return mask
    }
}
