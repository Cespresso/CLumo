package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StandDisplaySettingsTest {
    private fun packet(count: Int = 8): ByteArray =
        ByteArray(78).also {
            it[0] = 4
            it[10] = 1
            it[11] = count.toByte()
            it[12] = 44
            it[13] = 1
            for (i in 0..7) it[14 + i * 8] = (i + 1).toByte()
            it[2] = 1
        }

    @Test fun validatesBrowserPayloadWithoutLosingFrames() {
        val bytes = packet()
        val parsed = requireNotNull(StandDisplaySettings.decode(bytes))
        assertArrayEquals(bytes, parsed.encode())
        bytes[14] = 9
        assertEquals(1, parsed.encode()[14].toInt())
        assertNotNull(StandDisplaySettings.decode(packet(1)))
        assertNotNull(StandDisplaySettings.decode(packet(0)))
    }

    @Test fun rejectsMalformedSettings() {
        assertNull(StandDisplaySettings.decode(ByteArray(10)))
        for ((offset, value) in listOf(0 to 16, 1 to 3, 10 to 2, 11 to 9, 2 to 8)) {
            assertNull(StandDisplaySettings.decode(packet().also { it[offset] = value.toByte() }))
        }
        assertNull(
            StandDisplaySettings.decode(
                packet().also {
                    it[12] = 99
                    it[13] = 0
                },
            ),
        )
        assertNull(
            StandDisplaySettings.decode(
                packet().also {
                    it[12] = -47
                    it[13] = 7
                },
            ),
        )
    }
}
