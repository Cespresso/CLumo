package io.github.cespresso.clumo.ui.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyPatternTest {
    @Test fun patternsRoundTripThroughJsonAndFirmwareWithoutOverwritingBrightness() {
        val scroll = StandbyPattern.presets[1].copy(mode = "scroll", duration = 600)
        for (pattern in StandbyPattern.presets + scroll + StandbyPattern.off) {
            val imported = StandbyPattern.parse(pattern.json().toString())
            assertEquals(pattern, imported)
            val settings = imported.settings(13)
            assertEquals(13, settings.encode()[0].toInt())
            assertTrue(pattern.matches(StandbyPattern.fromDevice(settings)))
        }
    }

    @Test fun malformedFramesAndUnsupportedVersionsAreRejected() {
        val valid = StandbyPattern.presets.first().json()
        for (bad in listOf(
            valid.toString() + " trailing",
            valid.put("version", 2).toString(),
            valid.put("version", 1).put("frames", org.json.JSONArray(listOf(listOf("0000000x")))).toString(),
        )) {
            assertTrue(runCatching { StandbyPattern.parse(bad) }.isFailure)
        }
    }
}
