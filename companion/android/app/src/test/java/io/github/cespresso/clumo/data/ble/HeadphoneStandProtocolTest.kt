package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeadphoneStandProtocolTest {
    @Test fun validatesExactInfoAndDedicatedDiscovery() {
        assertTrue(HeadphoneStandProtocol.acceptsInfo(byteArrayOf(4, 1)))
        for (value in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(1, 1), byteArrayOf(1, 2), byteArrayOf(2, 1), byteArrayOf(1, 1, 0))) {
            assertFalse(HeadphoneStandProtocol.acceptsInfo(value))
        }
        // Firmware v4 exposes these nine dedicated characteristics.
        val discovered = (1..9).map {
            UUID.fromString("d973000$it-b19e-4c81-a5e8-36fbd47a9c10")
        }.toSet()
        assertEquals(GattCompatibilityAction.ACCEPT, gattCompatibilityAction(discovered, HeadphoneStandProtocol.REQUIRED, emptySet(), false))
        assertEquals(
            listOf(2, 3, 4, 6, 8).map { UUID.fromString("d973000$it-b19e-4c81-a5e8-36fbd47a9c10") },
            HeadphoneStandProtocol.INITIAL_READS,
        )
        assertFalse(HeadphoneStandProtocol.REQUIRED.contains(BleUuids.TIMER))
    }

    @Test fun rejectsInvalidValuesAndLegacyWrites() {
        assertFalse(HeadphoneStandProtocol.allowsWrite(BleUuids.MODE, byteArrayOf(3)))
        assertFalse(HeadphoneStandProtocol.allowsWrite(BleUuids.TIMER, byteArrayOf(1)))
        assertFalse(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VOLUME, byteArrayOf(101)))
        assertTrue(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VOLUME, byteArrayOf(0)))
        assertTrue(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VOLUME, byteArrayOf(1, 100)))
        for (packet in listOf(byteArrayOf(-1), byteArrayOf(100), byteArrayOf(1), byteArrayOf(0, 0), byteArrayOf(1, 101))) {
            assertFalse(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VOLUME, packet))
        }
        assertTrue(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VOLUME, byteArrayOf(1, 0)))
        assertFalse(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.VISUALIZER, ByteArray(8) { 9 }))
        assertFalse(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.DISPLAY_FRAME, ByteArray(7)))
        assertFalse(HeadphoneStandProtocol.allowsWrite(HeadphoneStandProtocol.STANDBY, byteArrayOf(3)))
    }
}
