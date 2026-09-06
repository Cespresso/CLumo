package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPickerStateTest {

    @Test
    fun physicalPaletteStartsWithBrandColorsAndIncludesWhite() {
        assertEquals("#7E9E7C", PHYSICAL_PART_PRESETS[0].color.toHex())
        assertEquals("#E8907E", PHYSICAL_PART_PRESETS[1].color.toHex())
        assertTrue(PHYSICAL_PART_PRESETS.any { it.color.toHex() == "#FFFFFF" })
    }

    @Test
    fun ledPaletteStartsWithFixedModulePrimaryColors() {
        assertEquals(
            listOf("#E85D53", "#538BC5", "#54A36A"),
            LED_PRESETS.take(3).map { it.color.toHex() },
        )
    }

    @Test
    fun invalidHexDisablesConfirmationWithoutChangingCommittedColor() {
        val initial = color("#538BC5")

        val state = ColorPickerState(initial).withHexInput("#123")

        assertFalse(state.canConfirm)
        assertEquals(initial, state.committedColor)
        assertNull(state.confirmedColorOrNull())
    }

    @Test
    fun validHexUpdatesDraftAndConfirmationReturnsIt() {
        val state = ColorPickerState(color("#538BC5")).withHexInput("e85d53")

        assertTrue(state.canConfirm)
        assertEquals(color("#E85D53"), state.draftColor)
        assertEquals(color("#E85D53"), state.confirmedColorOrNull())
    }

    @Test
    fun hsvSelectionKeepsHexFieldInSync() {
        val state = ColorPickerState(color("#000000")).withHsv(120f, 1f, 1f)

        assertEquals(color("#00FF00"), state.draftColor)
        assertEquals("#00FF00", state.hexInput)
        assertTrue(state.canConfirm)
    }

    private fun color(hex: String): RgbColor = requireNotNull(RgbColor.parseOrNull(hex))
}
