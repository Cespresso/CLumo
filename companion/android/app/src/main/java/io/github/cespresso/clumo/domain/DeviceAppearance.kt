package io.github.cespresso.clumo.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An opaque sRGB color stored without any UI-framework dependency. */
data class RgbColor private constructor(val value: Int) {

    fun toHex(): String = "#%06X".format(value)

    fun toHsv(): FloatArray {
        val red = ((value shr 16) and 0xFF) / 255f
        val green = ((value shr 8) and 0xFF) / 255f
        val blue = (value and 0xFF) / 255f
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        val delta = maximum - minimum
        val hue = when {
            delta == 0f -> 0f
            maximum == red -> 60f * (((green - blue) / delta) % 6f)
            maximum == green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (maximum == 0f) 0f else delta / maximum
        return floatArrayOf(hue, saturation, maximum)
    }

    companion object {
        fun of(value: Int): RgbColor = RgbColor(value and 0xFFFFFF)

        fun parseOrNull(raw: String): RgbColor? {
            val normalized = raw.removePrefix("#")
            if (!normalized.matches(Regex("[0-9A-Fa-f]{6}"))) return null
            return RgbColor(normalized.toInt(16))
        }

        fun fromHsv(hue: Float, saturation: Float, value: Float): RgbColor {
            val normalizedHue = ((hue % 360f) + 360f) % 360f
            val normalizedSaturation = saturation.coerceIn(0f, 1f)
            val normalizedValue = value.coerceIn(0f, 1f)
            val chroma = normalizedValue * normalizedSaturation
            val segment = normalizedHue / 60f
            val secondary = chroma * (1f - abs(segment % 2f - 1f))
            val (red, green, blue) = when (segment.toInt()) {
                0 -> Triple(chroma, secondary, 0f)
                1 -> Triple(secondary, chroma, 0f)
                2 -> Triple(0f, chroma, secondary)
                3 -> Triple(0f, secondary, chroma)
                4 -> Triple(secondary, 0f, chroma)
                else -> Triple(chroma, 0f, secondary)
            }
            val match = normalizedValue - chroma
            val packed = (((red + match) * 255f).roundToInt() shl 16) or
                (((green + match) * 255f).roundToInt() shl 8) or
                ((blue + match) * 255f).roundToInt()
            return RgbColor(packed)
        }
    }
}

/** Physical appearance of one known CLumo device. */
data class DeviceAppearance(
    val enclosureColor: RgbColor,
    val buttonAColor: RgbColor,
    val buttonBColor: RgbColor,
    val ledColor: RgbColor,
) {
    companion object {
        val DEFAULT = DeviceAppearance(
            enclosureColor = requireNotNull(RgbColor.parseOrNull("#7E9E7C")),
            buttonAColor = requireNotNull(RgbColor.parseOrNull("#E8907E")),
            buttonBColor = requireNotNull(RgbColor.parseOrNull("#FFFFFF")),
            ledColor = requireNotNull(RgbColor.parseOrNull("#F0A35E")),
        )
    }
}
