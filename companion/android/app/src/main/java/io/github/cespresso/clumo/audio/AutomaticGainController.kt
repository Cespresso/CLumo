package io.github.cespresso.clumo.audio

import kotlin.math.exp

internal data class AutomaticGainUpdate(
    val multiplier: Double,
    val silent: Boolean,
)

internal class AutomaticGainController {
    var multiplier = 1.0
        private set

    fun update(frameLevel: Double, elapsedMs: Long): AutomaticGainUpdate {
        if (!frameLevel.isFinite() || frameLevel <= 0.05) {
            return AutomaticGainUpdate(multiplier, silent = true)
        }

        val target = if (frameLevel < 1.0) 1.0 / frameLevel else 1.0
        val timeConstant = if (target > multiplier) 800.0 else 150.0
        val alpha = 1.0 - exp(-elapsedMs.coerceAtLeast(0) / timeConstant)
        multiplier = (multiplier + (target - multiplier) * alpha).coerceAtLeast(1.0)
        return AutomaticGainUpdate(multiplier, silent = false)
    }

    fun reset() {
        multiplier = 1.0
    }
}
