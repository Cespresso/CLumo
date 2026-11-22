package io.github.cespresso.clumo.ui.components.product

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProductDisplayStateTest {
    @Test
    fun onlyReadyShowsLiveBitsAndBit63KeepsRowMajorOrder() {
        val live = ProductDisplayState(connectionState = ConnectionState.Ready, bits = 1L or Long.MIN_VALUE)
        val pixels = live.ledRgba()
        assertEquals(255, pixels[3].toInt() and 255)
        assertEquals(0, pixels[7].toInt())
        assertEquals(pixels[3], pixels[63 * 4 + 3])
        ConnectionState.entries.filter { it != ConnectionState.Ready }.forEach { state ->
            val off = live.copy(connectionState = state).ledRgba()
            assertEquals(true, (0..63).all { off[it * 4 + 3].toInt() == 0 })
        }
    }

    @Test
    fun colorAndBrightnessAreIndependentForEachDevice() {
        val a = ProductDisplayState(
            connectionState = ConnectionState.Ready,
            bits = -1L,
            appearance = DeviceAppearance.DEFAULT.copy(ledColor = RgbColor.of(0xFF0000)),
        )
        val b = a.copy(appearance = a.appearance.copy(ledColor = RgbColor.of(0x0000FF)))
        assertNotEquals(pixel(a.ledRgba(), 0), pixel(b.ledRgba(), 0))
        assertEquals(0xFF0000, pixel(a.ledRgba(), 0))
        val levels = (0..15).map { a.copy(brightnessLevel = it).ledRgba()[3].toInt() and 255 }
        assertEquals(8, levels.first())
        assertEquals(255, levels.last())
        assertEquals(16, levels.distinct().size)
    }

    private fun pixel(bytes: ByteArray, index: Int): Int =
        ((bytes[index * 4].toInt() and 255) shl 16) or
            ((bytes[index * 4 + 1].toInt() and 255) shl 8) or (bytes[index * 4 + 2].toInt() and 255)
}
