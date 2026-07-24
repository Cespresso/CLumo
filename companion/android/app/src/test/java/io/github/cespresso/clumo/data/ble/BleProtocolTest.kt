package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleProtocolTest {
    @Test
    fun exposesModesInDisplayOrder() {
        assertEquals(listOf(0, 1, 2, 3), BleUuids.MODE_ORDER)
        assertEquals(0, BleUuids.MODE_POMODORO)
        assertEquals(1, BleUuids.MODE_TIMER)
        assertEquals(2, BleUuids.MODE_DISPLAY)
        assertEquals(3, BleUuids.MODE_VISUALIZER)
    }

    @Test
    fun buildsOnlyValidTimerDurationPayloads() {
        assertArrayEquals(
            byteArrayOf(0x10, 59, 59),
            BleUuids.timerSetDurationPayloadOrNull(59, 59),
        )
        assertArrayEquals(
            byteArrayOf(0x10, 0, 1),
            BleUuids.timerSetDurationPayloadOrNull(0, 1),
        )
        assertNull(BleUuids.timerSetDurationPayloadOrNull(0, 0))
        assertNull(BleUuids.timerSetDurationPayloadOrNull(60, 0))
        assertNull(BleUuids.timerSetDurationPayloadOrNull(0, 60))
    }
}
