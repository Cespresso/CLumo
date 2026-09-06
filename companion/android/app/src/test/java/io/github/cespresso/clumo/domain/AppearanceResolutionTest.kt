package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceResolutionTest {

    @Test
    fun knownIdReturnsItsProfile() {
        val custom = DeviceAppearance.DEFAULT.copy(ledColor = color("#538BC5"))

        assertEquals(custom, resolveAppearance("known", mapOf("known" to custom)))
    }

    @Test
    fun missingIdOrProfileReturnsCanonicalDefault() {
        assertEquals(DeviceAppearance.DEFAULT, resolveAppearance(null, emptyMap()))
        assertEquals(DeviceAppearance.DEFAULT, resolveAppearance("missing", emptyMap()))
    }

    private fun color(hex: String): RgbColor = requireNotNull(RgbColor.parseOrNull(hex))
}
