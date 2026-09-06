package io.github.cespresso.clumo.audio

import android.media.audiofx.Visualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVisualizerManagerTest {
    @Test
    fun manualGainKeepsApprovedEndpoints() {
        assertEquals(0.8, visualizerGain(0f), 0.0001)
        assertEquals(16.0, visualizerGain(1f), 0.0001)
    }

    @Test
    fun effectiveGainCannotExceedSixteen() {
        assertEquals(16.0, effectiveVisualizerGain(1f, 10.0, true), 0.0001)
    }

    @Test
    fun disabledAutomaticBoostUsesOnlyManualGain() {
        assertEquals(
            visualizerGain(0.6f),
            effectiveVisualizerGain(0.6f, 10.0, false),
            0.0001,
        )
    }

    @Test
    fun heightConversionClampsSuppliedGain() {
        assertEquals(
            visualizerBandHeight(5f, effectiveGain = 16.0),
            visualizerBandHeight(5f, effectiveGain = 40.0),
        )
    }

    @Test
    fun usesPlaybackVolumeScalingForFftCapture() {
        assertEquals(Visualizer.SCALING_MODE_AS_PLAYED, audioVisualizerScalingMode())
    }

    @Test
    fun keepsObservedLowVolumeMagnitudeVisibleAtMaximumSensitivity() {
        assertEquals(8, visualizerBandHeight(2.8f, visualizerGain(1f)))
    }

    @Test
    fun boostsMinimumPlaybackVolumeAtMaximumSensitivity() {
        assertEquals(4, visualizerBandHeight(0.5f, visualizerGain(1f)))
    }

    @Test
    fun keepsMidSensitivityGradual() {
        assertEquals(5, visualizerBandHeight(2.8f, visualizerGain(0.5f)))
    }

    @Test
    fun keepsLowVolumeVisibleAtMinimumSensitivity() {
        assertEquals(1, visualizerBandHeight(0.5f, visualizerGain(0f)))
    }

    @Test
    fun keepsSilenceOff() {
        assertEquals(0, visualizerBandHeight(0f, visualizerGain(1f)))
    }

    @Test
    fun increasesHeightForStrongerMagnitude() {
        val low = visualizerBandHeight(0.5f, visualizerGain(1f))
        val high = visualizerBandHeight(1f, visualizerGain(1f))

        assertTrue(high > low)
    }

    @Test
    fun increasesHeightWithSensitivity() {
        val low = visualizerBandHeight(0.5f, visualizerGain(0f))
        val high = visualizerBandHeight(0.5f, visualizerGain(1f))

        assertTrue(high > low)
    }

    @Test
    fun clampsStrongMagnitudeToDisplayHeight() {
        assertEquals(8, visualizerBandHeight(80f, visualizerGain(1f)))
    }
}
