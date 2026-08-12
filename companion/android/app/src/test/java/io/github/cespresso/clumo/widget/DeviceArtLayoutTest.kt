package io.github.cespresso.clumo.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout is what keeps the widget's device looking like the app's, so these lock the
 * proportions rather than the pixel values: everything is a fraction of the face.
 */
class DeviceArtLayoutTest {

    private val face = 188f

    @Test
    fun theFaceKeepsTheAppsFrameProportions() {
        val layout = deviceArtLayout(face, ringed = false)
        // At a 188 face the app's fractions are the dp values it declares.
        assertEquals(42f, layout.frameCorner, 0.01f)
        assertEquals(23f, layout.framePadding, 0.01f)
        assertEquals(21f, layout.panelCorner, 0.01f)
        assertEquals(13f, layout.gridPadding, 0.01f)
    }

    @Test
    fun theKnobsKeepTheAppsOwnDenominator() {
        // Knob ratios are fractions of 168 while the frame's are fractions of 188. Feeding a
        // 168 face makes the app's declared knob dp values fall out directly.
        val layout = deviceArtLayout(facePx = 168f, ringed = false)
        assertEquals(18f, layout.knobCapWidth, 0.01f)
        assertEquals(10f, layout.knobCapHeight, 0.01f)
        assertEquals(28f, layout.knobBossWidth, 0.01f)
        assertEquals(8f, layout.knobBossHeight, 0.01f)
        assertEquals(16f, layout.knobGap, 0.01f)
    }

    @Test
    fun theCapIsNarrowerThanTheBossSoTheStepReads() {
        val layout = deviceArtLayout(face, ringed = false)
        assertTrue(layout.knobCapWidth < layout.knobBossWidth)
    }

    @Test
    fun knobsSitAboveTheFace() {
        val layout = deviceArtLayout(face, ringed = false)
        assertEquals(layout.knobCapHeight + layout.knobBossHeight, layout.faceTop, 0.01f)
    }

    @Test
    fun anUnringedDeviceIsExactlyAsWideAsItsFace() {
        val layout = deviceArtLayout(face, ringed = false)
        assertEquals(0f, layout.ringInset, 0.01f)
        assertEquals(face, layout.widthPx, 0.01f)
        assertEquals(layout.faceTop + face, layout.heightPx, 0.01f)
    }

    @Test
    fun theRingAddsRoomOnEverySideOfTheFace() {
        val layout = deviceArtLayout(face, ringed = true)
        assertTrue(layout.ringInset > 0f)
        assertEquals(layout.ringInset, layout.faceLeft, 0.01f)
        assertEquals(face + layout.ringInset * 2f, layout.widthPx, 0.01f)
    }

    /** A ring drawn taller than the knob band would be clipped off the top of the bitmap. */
    @Test
    fun theRingFitsUnderTheKnobBand() {
        val layout = deviceArtLayout(face, ringed = true)
        assertTrue(layout.ringInset < layout.faceTop)
    }

    @Test
    fun everyDimensionScalesWithTheFace() {
        val small = deviceArtLayout(64f, ringed = true)
        val large = deviceArtLayout(128f, ringed = true)
        assertEquals(2f, large.widthPx / small.widthPx, 0.001f)
        assertEquals(2f, large.heightPx / small.heightPx, 0.001f)
        assertEquals(2f, large.knobBossWidth / small.knobBossWidth, 0.001f)
        assertEquals(2f, large.frameCorner / small.frameCorner, 0.001f)
    }

    @Test
    fun theGridFitsInsideThePanel() {
        val layout = deviceArtLayout(face, ringed = true)
        val panel = layout.facePx - layout.framePadding * 2f
        assertTrue(panel > 0f)
        assertTrue(panel - layout.gridPadding * 2f > 0f)
    }
}
