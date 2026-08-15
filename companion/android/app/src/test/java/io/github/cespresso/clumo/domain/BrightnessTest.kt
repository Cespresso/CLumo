package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessTest {
    @Test
    fun theSliderSpansTheWholeDeviceRange() {
        assertEquals(0, Brightness.toLevel(0f))
        assertEquals(Brightness.MAX_LEVEL, Brightness.toLevel(100f))
        assertEquals(0f, Brightness.toPercent(0), 0.001f)
        assertEquals(100f, Brightness.toPercent(Brightness.MAX_LEVEL), 0.001f)
    }

    @Test
    fun everyLevelSurvivesARoundTripThroughTheSlider() {
        // The device echoes back what it accepted. If a level did not land back on itself the
        // slider would jump under the finger every time that echo arrived.
        for (level in 0..Brightness.MAX_LEVEL) {
            assertEquals(level, Brightness.toLevel(Brightness.toPercent(level)))
        }
    }

    @Test
    fun theSliderIsClampedToTheDeviceRange() {
        assertEquals(0, Brightness.toLevel(-20f))
        assertEquals(Brightness.MAX_LEVEL, Brightness.toLevel(180f))
    }

    @Test
    fun aLevelIsOnlyWrittenWhenItDiffersFromTheDevice() {
        // Writing back what the device just reported would restart the same echo forever.
        assertFalse(Brightness.shouldWrite(level = 7, deviceLevel = 7))
        assertTrue(Brightness.shouldWrite(level = 8, deviceLevel = 7))
    }

    @Test
    fun aDarkDeviceIsStillVisibleOnScreen() {
        // The device is off; the drawing of it is merely dim.
        assertEquals(0.4f, Brightness.litAlpha(0), 0.001f)
        assertEquals(1f, Brightness.litAlpha(Brightness.MAX_LEVEL), 0.001f)
    }

    @Test
    fun bothBrightnessScalesDimTheFaceTheSameWay() {
        for (level in 0..Brightness.MAX_LEVEL) {
            assertEquals(
                Brightness.litAlpha(level),
                Brightness.litAlphaForPercent(Brightness.toPercent(level)),
                0.001f,
            )
        }
    }

    @Test
    fun anOutOfRangeBrightnessNeverDrivesTheFaceOutOfRange() {
        assertEquals(0.4f, Brightness.litAlpha(-3), 0.001f)
        assertEquals(1f, Brightness.litAlpha(99), 0.001f)
        assertEquals(0.4f, Brightness.litAlphaForPercent(-10f), 0.001f)
        assertEquals(1f, Brightness.litAlphaForPercent(150f), 0.001f)
    }
}
