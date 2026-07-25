package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonEventTest {

    @Test
    fun decodesDisplayAndVisualizerPresses() {
        assertEquals(
            ButtonEvent(BleUuids.MODE_DISPLAY, BleUuids.BUTTON_MAIN),
            parseButtonEvent(byteArrayOf(2, 0)),
        )
        assertEquals(
            ButtonEvent(BleUuids.MODE_VISUALIZER, BleUuids.BUTTON_SUB),
            parseButtonEvent(byteArrayOf(3, 1)),
        )
    }

    @Test
    fun ignoresTruncatedPayloads() {
        assertNull(parseButtonEvent(byteArrayOf()))
        assertNull(parseButtonEvent(byteArrayOf(2)))
    }

    @Test
    fun ignoresUnknownModeOrButton() {
        assertNull(parseButtonEvent(byteArrayOf(9, 0)))
        assertNull(parseButtonEvent(byteArrayOf(2, 9)))
    }

    @Test
    fun ignoresTrailingBytes() {
        assertEquals(
            ButtonEvent(BleUuids.MODE_DISPLAY, BleUuids.BUTTON_MAIN),
            parseButtonEvent(byteArrayOf(2, 0, 7)),
        )
    }
}
