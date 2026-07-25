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

    @Test
    fun steppingUpAddsOneTenthWithoutFloatDrift() {
        assertEquals(0.7f, steppedVisualizerSensitivity(0.6f, up = true))
        assertEquals(0.8f, steppedVisualizerSensitivity(0.7f, up = true))
    }

    @Test
    fun steppingDownSubtractsOneTenthWithoutFloatDrift() {
        assertEquals(0.5f, steppedVisualizerSensitivity(0.6f, up = false))
        assertEquals(0.4f, steppedVisualizerSensitivity(0.5f, up = false))
    }

    @Test
    fun steppingStopsAtTheRangeBounds() {
        assertEquals(1f, steppedVisualizerSensitivity(1f, up = true))
        assertEquals(0f, steppedVisualizerSensitivity(0f, up = false))
    }

    @Test
    fun steppingFromAnOutOfRangeValueSanitizesFirst() {
        assertEquals(0.1f, steppedVisualizerSensitivity(-5f, up = true))
        assertEquals(0.9f, steppedVisualizerSensitivity(5f, up = false))
        assertEquals(0.7f, steppedVisualizerSensitivity(Float.NaN, up = true))
    }
}
