package io.github.cespresso.clumo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.accentContentToneFor
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import io.github.cespresso.clumo.ui.components.toComposeColor

/**
 * Framework-free accent roles derived from the primary device's appearance:
 * - [accent]: enclosure color, the active/positive accent and brand decor
 * - [cta]: button A color, call-to-action fills
 * - [knob]: button B color, movable parts sitting on tinted surfaces
 * - [led]: default dot color for device-face rendering
 */
data class AccentSpec(
    val accent: RgbColor,
    val accentTone: ContentTone,
    val cta: RgbColor,
    val ctaTone: ContentTone,
    val knob: RgbColor,
    val led: RgbColor,
)

fun accentSpecFor(appearance: DeviceAppearance): AccentSpec =
    AccentSpec(
        accent = appearance.enclosureColor,
        accentTone = accentContentToneFor(appearance.enclosureColor),
        cta = appearance.buttonAColor,
        ctaTone = accentContentToneFor(appearance.buttonAColor),
        knob = appearance.buttonBColor,
        led = appearance.ledColor,
    )

/** Compose-side accent palette. Error/destructive colors stay in [ClumoColors]. */
@Immutable
data class ClumoAccents(
    val accent: Color,
    val onAccent: Color,
    val cta: Color,
    val onCta: Color,
    val knob: Color,
    val led: Color,
)

private fun ContentTone.toContentColor(): Color = if (this == ContentTone.Dark) ClumoColors.Text else ClumoColors.White

fun AccentSpec.toClumoAccents(): ClumoAccents =
    ClumoAccents(
        accent = accent.toComposeColor(),
        onAccent = accentTone.toContentColor(),
        cta = cta.toComposeColor(),
        onCta = ctaTone.toContentColor(),
        knob = knob.toComposeColor(),
        led = led.toComposeColor(),
    )

val LocalClumoAccents = compositionLocalOf {
    accentSpecFor(DeviceAppearance.DEFAULT).toClumoAccents()
}
