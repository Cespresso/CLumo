package io.github.cespresso.clumo.domain

/**
 * A saved 8x8 display pattern. [bits] is a 64-char string of '0'/'1' in
 * row-major order (row 0 = top, first char of each row = leftmost column).
 */
data class Pattern(
    val id: String,
    val name: String,
    val bits: String,
) {
    /** Row bitmap bytes as expected by the DISPLAY characteristic (MSB = left). */
    fun toRowBytes(): ByteArray = bitsToRowBytes(bits)

    companion object {
        const val EMPTY_BITS_LENGTH = 64

        fun bitsToRowBytes(bits: String): ByteArray {
            val normalized = bits.padEnd(EMPTY_BITS_LENGTH, '0').take(EMPTY_BITS_LENGTH)
            return ByteArray(8) { row ->
                var b = 0
                for (col in 0 until 8) {
                    if (normalized[row * 8 + col] == '1') b = b or (1 shl (7 - col))
                }
                b.toByte()
            }
        }
    }
}
