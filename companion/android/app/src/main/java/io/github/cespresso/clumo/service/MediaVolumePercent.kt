package io.github.cespresso.clumo.service

import kotlin.math.roundToInt

/** Unknown is distinct from a confirmed zero volume. */
internal fun mediaVolumePercent(current: Int, maximum: Int): Int? = if (maximum <= 0 || current !in 0..maximum) null else (100.0 * current / maximum).roundToInt()
