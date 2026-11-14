package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlBindingsTest {
    @Test fun defaultAndEverySupportedActionRoundTrip() {
        assertArrayEquals(byteArrayOf(1, 2, 3, 1, 3, 2, 1), ControlBindings.DEFAULT.encode())
        for (index in 0..4) {
            for (action in 0..7) {
                val binding = ControlBindings.DEFAULT.changed(index, action)
                assertEquals(action, requireNotNull(ControlBindings.decode(binding.encode())).value(index))
            }
        }
        for (value in 0..1) assertEquals(value, ControlBindings.DEFAULT.changed(5, value).value(5))
    }

    @Test fun invalidPacketsAreRejectedAndArraysAreCopied() {
        for (size in listOf(0, 6, 8)) assertNull(ControlBindings.decode(ByteArray(size)))
        for (index in 0..6) {
            val bytes = ControlBindings.DEFAULT.encode()
            bytes[index] = 127
            assertNull(ControlBindings.decode(bytes))
        }
        val bytes = ControlBindings.DEFAULT.encode()
        val binding = requireNotNull(ControlBindings.decode(bytes))
        bytes[1] = 7
        binding.encode()[1] = 6
        assertEquals(2, binding.value(0))
    }
}
