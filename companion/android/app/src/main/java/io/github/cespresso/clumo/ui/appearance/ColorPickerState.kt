package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.RgbColor

data class ColorPickerState(
    val committedColor: RgbColor,
    val draftColor: RgbColor = committedColor,
    val hexInput: String = committedColor.toHex(),
) {
    val canConfirm: Boolean
        get() = RgbColor.parseOrNull(hexInput) != null

    fun withHexInput(input: String): ColorPickerState {
        val parsed = RgbColor.parseOrNull(input)
        return copy(
            draftColor = parsed ?: draftColor,
            hexInput = input,
        )
    }

    fun withHsv(hue: Float, saturation: Float, value: Float): ColorPickerState {
        val selected = RgbColor.fromHsv(hue, saturation, value)
        return copy(draftColor = selected, hexInput = selected.toHex())
    }

    fun confirmedColorOrNull(): RgbColor? =
        if (canConfirm) RgbColor.parseOrNull(hexInput) else null
}
