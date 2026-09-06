package io.github.cespresso.clumo.design

import io.github.cespresso.clumo.domain.RgbColor
import kotlin.math.pow

/** Which of the two content colors stays legible on a given fill. */
enum class ContentTone { Dark, Light }

fun contentToneFor(color: RgbColor): ContentTone = if (relativeLuminance(color) > 0.179f) ContentTone.Dark else ContentTone.Light

/**
 * Content tone for text/icons on an accent fill. Unlike contentToneFor's
 * WCAG cutoff, mid-tone fills such as the default sage and coral keep white
 * content to match the design language; only clearly light fills switch to
 * dark content.
 */
fun accentContentToneFor(color: RgbColor): ContentTone = if (relativeLuminance(color) > 0.4f) ContentTone.Dark else ContentTone.Light

private fun relativeLuminance(color: RgbColor): Float {
    fun linear(channel: Int): Float {
        val normalized = channel / 255f
        return if (normalized <= 0.04045f) {
            normalized / 12.92f
        } else {
            ((normalized + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    val red = linear((color.value shr 16) and 0xFF)
    val green = linear((color.value shr 8) and 0xFF)
    val blue = linear(color.value and 0xFF)
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
