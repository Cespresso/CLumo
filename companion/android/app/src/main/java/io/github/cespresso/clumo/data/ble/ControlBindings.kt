package io.github.cespresso.clumo.data.ble

/** Version 1: five discrete gestures and one rotation pair. */
class ControlBindings private constructor(private val data: ByteArray) {
    fun encode() = data.copyOf()
    fun value(index: Int) = data[index + 1].toInt()
    fun changed(index: Int, action: Int): ControlBindings = requireNotNull(decode(data.copyOf().also { it[index + 1] = action.toByte() }))
    companion object {
        val DEFAULT get() = requireNotNull(decode(byteArrayOf(1, 2, 3, 1, 3, 2, 1)))
        fun decode(bytes: ByteArray): ControlBindings? = if (bytes.size == 7 && bytes[0] == 1.toByte() && bytes.slice(1..5).all { it.toInt() in 0..7 } && bytes[6].toInt() in 0..1) ControlBindings(bytes.copyOf()) else null
    }
}
