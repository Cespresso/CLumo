package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.DeviceMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleProtocolTest {
    @Test
    fun exposesModesInDisplayOrder() {
        assertEquals(listOf(0, 1, 2, 3), DeviceMode.ORDER)
        assertEquals(0, DeviceMode.POMODORO)
        assertEquals(1, DeviceMode.TIMER)
        assertEquals(2, DeviceMode.DISPLAY)
        assertEquals(3, DeviceMode.VISUALIZER)
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
