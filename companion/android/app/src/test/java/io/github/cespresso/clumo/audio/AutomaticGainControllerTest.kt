package io.github.cespresso.clumo.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticGainControllerTest {
    @Test
    fun silenceDoesNotChargeGain() {
        val controller = AutomaticGainController()
        val result = controller.update(0.05, 800)

        assertTrue(result.silent)
        assertEquals(1.0, result.multiplier, 0.0001)
    }

    @Test
    fun weakInputRaisesGainGradually() {
        val controller = AutomaticGainController()
        val first = controller.update(0.5, 100).multiplier
        val later = controller.update(0.5, 700).multiplier

        assertTrue(first in 1.0..2.0)
        assertTrue(later > first)
    }

    @Test
    fun loudInputReleasesGain() {
        val controller = AutomaticGainController()
        val boosted = controller.update(0.5, 800).multiplier
        val released = controller.update(1.0, 150).multiplier

        assertTrue(released < boosted)
    }

    @Test
    fun resetReturnsToOne() {
        val controller = AutomaticGainController()
        controller.update(0.5, 800)
        controller.reset()

        assertEquals(1.0, controller.multiplier, 0.0001)
    }

    @Test
    fun invalidLevelsAreSilent() {
        val controller = AutomaticGainController()

        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertTrue(controller.update(it, 80).silent)
        }
    }
}
