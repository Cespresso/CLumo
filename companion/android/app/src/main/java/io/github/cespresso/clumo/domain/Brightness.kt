package io.github.cespresso.clumo.domain

import kotlin.math.roundToInt

/**
 * The two brightness scales and the rules that keep them agreeing: the device takes 16 levels,
 * the slider offers a percentage, and the drawing of the device dims along with it.
 */
object Brightness {

    /** The firmware accepts 0..15. */
    const val MAX_LEVEL = 15

    /** The slider's percentage as a level the device will accept. */
    fun toLevel(percent: Float): Int =
        (percent / 100f * MAX_LEVEL).roundToInt().coerceIn(0, MAX_LEVEL)

    /** A level the device reported as a position for the slider. */
    fun toPercent(level: Int): Float = level.coerceIn(0, MAX_LEVEL) / MAX_LEVEL.toFloat() * 100f

    /**
     * Whether a level is worth sending. The device echoes every accepted write back as a
     * notification, so re-sending what it just reported would keep the two chasing each other.
     */
    fun shouldWrite(level: Int, deviceLevel: Int): Boolean = level != deviceLevel

    /** How solid the drawn face is at a device level. */
    fun litAlpha(level: Int): Float = alphaFor(level.coerceIn(0, MAX_LEVEL) / MAX_LEVEL.toFloat())

    /** The same rule for callers that only hold the slider's percentage. */
    fun litAlphaForPercent(percent: Float): Float = alphaFor(percent / 100f)

    // A face at zero brightness still has to be visible on screen: the device is dark, the
    // drawing of it is merely dim.
    private fun alphaFor(fraction: Float): Float = 0.4f + fraction.coerceIn(0f, 1f) * 0.6f
}
