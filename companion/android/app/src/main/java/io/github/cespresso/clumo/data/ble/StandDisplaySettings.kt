package io.github.cespresso.clumo.data.ble

/** The exact Settings payload shared with the firmware and browser editor. */
class StandDisplaySettings private constructor(private val bytes: ByteArray) {
    fun encode(): ByteArray = bytes.copyOf()

    fun framePeriod(mode: Int?): Long =
        when {
            mode == 1 -> 600L
            mode == 0 && bytes[11] > 1 -> ((bytes[12].toInt() and 255) + 256 * (bytes[13].toInt() and 255)).toLong()
            else -> 0L
        }

    fun frameBits(mode: Int?, step: Int): Long {
        if (mode == 2) return 0L
        val offset = if (mode == 0 && bytes[11] > 0) 14 + 8 * (step % bytes[11]) else 2
        val rows = bytes.copyOfRange(offset, offset + 8)
        if (mode == 1) {
            for (i in rows.indices) {
                val value = rows[i].toInt() and 255
                val shift = step % 8
                rows[i] = ((value shl shift) or (value ushr (8 - shift))).toByte()
            }
        }
        return io.github.cespresso.clumo.domain.FaceBits.fromRowBytes(rows)
    }

    companion object {
        const val SIZE = 78
        fun decode(bytes: ByteArray): StandDisplaySettings? {
            fun u(index: Int) = bytes[index].toInt() and 255
            if (bytes.size != SIZE || u(0) > 15 || u(1) > 2 || u(10) != 1 || u(11) > 8) return null
            if (u(11) > 0) {
                if (u(12) + 256 * u(13) !in 100..2000) return null
                if (!bytes.copyOfRange(2, 10).contentEquals(bytes.copyOfRange(14, 22))) return null
            }
            return StandDisplaySettings(bytes.copyOf())
        }
    }
}
