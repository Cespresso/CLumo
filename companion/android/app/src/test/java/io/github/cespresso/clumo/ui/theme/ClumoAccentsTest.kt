package io.github.cespresso.clumo.ui.theme

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import io.github.cespresso.clumo.ui.components.ContentTone
import org.junit.Assert.assertEquals
import org.junit.Test

class ClumoAccentsTest {

    @Test
    fun defaultSpecPreservesTheLegacyPalette() {
        val spec = accentSpecFor(DeviceAppearance.DEFAULT)
        assertEquals("#7E9E7C", spec.accent.toHex())
        assertEquals("#E8907E", spec.cta.toHex())
        assertEquals("#FFFFFF", spec.knob.toHex())
        assertEquals("#F0A35E", spec.led.toHex())
        // Light tone means white content on the default sage and coral fills.
        assertEquals(ContentTone.Light, spec.accentTone)
        assertEquals(ContentTone.Light, spec.ctaTone)
    }

    @Test
    fun rolesMapFromAppearanceFields() {
        val appearance = DeviceAppearance(
            enclosureColor = color("#8CAAB5"),
            buttonAColor = color("#D1AD66"),
            buttonBColor = color("#3E3A36"),
            ledColor = color("#538BC5"),
        )
        val spec = accentSpecFor(appearance)
        assertEquals("#8CAAB5", spec.accent.toHex())
        assertEquals("#D1AD66", spec.cta.toHex())
        assertEquals("#3E3A36", spec.knob.toHex())
        assertEquals("#538BC5", spec.led.toHex())
    }

    @Test
    fun clearlyLightAccentFillsGetDarkContent() {
        assertEquals(ContentTone.Dark, accentContentToneFor(color("#FFFFFF")))
        assertEquals(ContentTone.Dark, accentContentToneFor(color("#FFF4DC")))
    }

    @Test
    fun midToneAndDarkAccentFillsGetLightContent() {
        assertEquals(ContentTone.Light, accentContentToneFor(color("#7E9E7C")))
        assertEquals(ContentTone.Light, accentContentToneFor(color("#E8907E")))
        assertEquals(ContentTone.Light, accentContentToneFor(color("#3E3A36")))
    }

    private fun color(hex: String): RgbColor = requireNotNull(RgbColor.parseOrNull(hex))
}
