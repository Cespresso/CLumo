package io.github.cespresso.clumo.design

import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentToneTest {

    @Test
    fun lightColorsUseDarkContent() {
        assertEquals(ContentTone.Dark, contentToneFor(color("#FFFFFF")))
        assertEquals(ContentTone.Dark, contentToneFor(color("#FFF4DC")))
    }

    @Test
    fun darkColorsUseLightContent() {
        assertEquals(ContentTone.Light, contentToneFor(color("#000000")))
        assertEquals(ContentTone.Light, contentToneFor(color("#111111")))
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
