package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.DeviceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonEventTest {

    @Test
    fun decodesDisplayAndVisualizerPresses() {
        assertEquals(
            ButtonEvent(DeviceMode.DISPLAY, BleUuids.BUTTON_MAIN),
            ButtonEvent.parse(byteArrayOf(2, 0)),
        )
        assertEquals(
            ButtonEvent(DeviceMode.VISUALIZER, BleUuids.BUTTON_SUB),
            ButtonEvent.parse(byteArrayOf(3, 1)),
        )
        assertTrue(ButtonEvent.parse(byteArrayOf(2, 0))!!.isMain)
        assertFalse(ButtonEvent.parse(byteArrayOf(3, 1))!!.isMain)
    }

    @Test
    fun ignoresTruncatedPayloads() {
        assertNull(ButtonEvent.parse(byteArrayOf()))
        assertNull(ButtonEvent.parse(byteArrayOf(2)))
    }

    @Test
    fun ignoresModesTheFirmwareNeverForwards() {
        assertNull(ButtonEvent.parse(byteArrayOf(0, 0)))
        assertNull(ButtonEvent.parse(byteArrayOf(1, 0)))
        assertNull(ButtonEvent.parse(byteArrayOf(9, 0)))
    }

    @Test
    fun ignoresUnknownButton() {
        assertNull(ButtonEvent.parse(byteArrayOf(2, 9)))
    }

    @Test
    fun ignoresTrailingBytes() {
        assertEquals(
            ButtonEvent(DeviceMode.DISPLAY, BleUuids.BUTTON_MAIN),
            ButtonEvent.parse(byteArrayOf(2, 0, 7)),
        )
    }
}
