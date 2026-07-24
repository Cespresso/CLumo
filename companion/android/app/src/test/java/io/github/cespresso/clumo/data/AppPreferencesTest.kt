package io.github.cespresso.clumo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun missingStoredVisualizerSensitivityUsesDefault() {
        assertEquals(0.6f, interpretStoredVisualizerSensitivity(null))
    }

    @Test
    fun inRangeStoredVisualizerSensitivityIsUnchanged() {
        assertEquals(0f, interpretStoredVisualizerSensitivity(0f))
        assertEquals(0.4f, interpretStoredVisualizerSensitivity(0.4f))
        assertEquals(1f, interpretStoredVisualizerSensitivity(1f))
    }

    @Test
    fun storedVisualizerSensitivityBelowRangeIsClamped() {
        assertEquals(0f, interpretStoredVisualizerSensitivity(-1f))
    }

    @Test
    fun storedVisualizerSensitivityAboveRangeIsClamped() {
        assertEquals(1f, interpretStoredVisualizerSensitivity(2f))
    }

    @Test
    fun storedNaNVisualizerSensitivityUsesDefault() {
        assertEquals(0.6f, interpretStoredVisualizerSensitivity(Float.NaN))
    }

    @Test
    fun storedPositiveInfinityVisualizerSensitivityUsesDefault() {
        assertEquals(0.6f, interpretStoredVisualizerSensitivity(Float.POSITIVE_INFINITY))
    }

    @Test
    fun storedNegativeInfinityVisualizerSensitivityUsesDefault() {
        assertEquals(0.6f, interpretStoredVisualizerSensitivity(Float.NEGATIVE_INFINITY))
    }
}
