package io.github.cespresso.clumo.data.ble

/** The exact Settings payload shared with the firmware and browser editor. */
class StandDisplaySettings private constructor(private val bytes: ByteArray) {
    fun encode(): ByteArray = bytes.copyOf()

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
