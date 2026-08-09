package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAppearanceTest {

    @Test
    fun defaultsMatchCurrentHardwareRendering() {
        assertEquals("#7E9E7C", DeviceAppearance.DEFAULT.enclosureColor.toHex())
        assertEquals("#E8907E", DeviceAppearance.DEFAULT.buttonAColor.toHex())
        assertEquals("#FFFFFF", DeviceAppearance.DEFAULT.buttonBColor.toHex())
        assertEquals("#F0A35E", DeviceAppearance.DEFAULT.ledColor.toHex())
    }

    @Test
    fun hexParserAcceptsOptionalHashAndNormalizesCase() {
        assertEquals("#8CAAB5", RgbColor.parseOrNull("8caab5")?.toHex())
        assertEquals("#8CAAB5", RgbColor.parseOrNull("#8CaAb5")?.toHex())
    }

    @Test
    fun hexParserRejectsAlphaAndMalformedValues() {
        listOf("#FF8CAAB5", "#123", "#GG0000", "").forEach {
            assertNull(RgbColor.parseOrNull(it))
        }
    }

    @Test
    fun rgbHsvRoundTripPreservesRepresentativeColors() {
        listOf("#000000", "#FFFFFF", "#E85D53", "#538BC5", "#54A36A").forEach { hex ->
            val original = requireNotNull(RgbColor.parseOrNull(hex))
            val hsv = original.toHsv()
            assertEquals(original, RgbColor.fromHsv(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test
    fun hsvInputsAreWrappedAndClamped() {
        assertEquals(
            RgbColor.fromHsv(0f, 1f, 1f),
            RgbColor.fromHsv(360f, 2f, 2f),
        )
        assertEquals("#000000", RgbColor.fromHsv(120f, 1f, -1f).toHex())
    }
}
