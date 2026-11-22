package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.data.decodeDeviceAppearances
import io.github.cespresso.clumo.data.encodeDeviceAppearances
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandThemesTest {
    @Test fun regroupingSurvivesReloadAndChangesOnlyMembers() {
        for (theme in standThemes) {
            val preset = theme.applyTo(DeviceAppearance.DEFAULT)
            assertTrue(theme.matches(preset))
            val assigned = preset.assign(AppearancePart.Clip, ColorGroup.Primary)
            val restored = decodeDeviceAppearances(encodeDeviceAppearances(mapOf("stand" to assigned))).getValue("stand")
            val green = RgbColor.of(0x117733)
            val updated = restored.recolor(AppearancePart.Clip, green)
            assertEquals(green, updated.enclosureColor)
            assertEquals(green, updated.colorFor(AppearancePart.Clip))
            assertEquals(preset.colorFor(AppearancePart.Accent), updated.colorFor(AppearancePart.Accent))
            val independent = updated.assign(AppearancePart.Clip, null).recolor(AppearancePart.Enclosure, RgbColor.of(0))
            assertEquals(green, independent.colorFor(AppearancePart.Clip))
        }
    }
}
