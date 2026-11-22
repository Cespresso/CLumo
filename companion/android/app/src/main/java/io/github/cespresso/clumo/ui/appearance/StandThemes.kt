package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor

internal enum class ColorGroup { Primary, Secondary, Accent, Detail, Highlight }

internal data class StandTheme(
    val name: String,
    val palette: Map<ColorGroup, RgbColor>,
    val parts: Map<AppearancePart, ColorGroup>,
) {
    fun applyTo(current: DeviceAppearance): DeviceAppearance =
        parts.entries.fold(
            current.copy(
                palette = palette.mapKeys { it.key.name },
                assignments = parts.mapKeys { it.key.name }.mapValues { it.value.name },
            ),
        ) { result, (part, group) -> result.withColor(part, palette.getValue(group)) }

    fun matches(current: DeviceAppearance) =
        parts.all { (part, group) ->
            current.assignments[part.name] == group.name && current.colorFor(part) == palette[group]
        } && current.assignments.size == parts.size
}

private fun standTheme(
    name: String,
    primary: Int,
    secondary: Int,
    accent: Int,
    detail: Int,
    highlight: Int,
    overrides: Map<AppearancePart, ColorGroup>,
): StandTheme {
    val parts = mapOf(
        AppearancePart.Enclosure to ColorGroup.Primary, AppearancePart.Panel to ColorGroup.Detail,
        AppearancePart.StandButtons to ColorGroup.Highlight, AppearancePart.Windows to ColorGroup.Highlight,
        AppearancePart.MatrixSurface to ColorGroup.Highlight, AppearancePart.Accent to ColorGroup.Accent,
        AppearancePart.Insert to ColorGroup.Accent, AppearancePart.Trim to ColorGroup.Accent, AppearancePart.PowerLens to ColorGroup.Accent,
        AppearancePart.Clip to ColorGroup.Accent, AppearancePart.Logo to ColorGroup.Accent,
        AppearancePart.FrontHolder to ColorGroup.Secondary, AppearancePart.RearHolder to ColorGroup.Accent,
    ) + overrides
    return StandTheme(
        name,
        mapOf(
            ColorGroup.Primary to primary,
            ColorGroup.Secondary to secondary,
            ColorGroup.Accent to accent,
            ColorGroup.Detail to detail,
            ColorGroup.Highlight to highlight,
        )
            .mapValues { RgbColor.of(it.value) },
        parts,
    )
}

private val ivorySage = standTheme(
    "Ivory & sage",
    0xE4E4D5,
    0xE7B84F,
    0x6A856C,
    0xBCBBAF,
    0xECEBDE,
    mapOf(
        AppearancePart.FrontHolder to ColorGroup.Accent,
        AppearancePart.RearHolder to ColorGroup.Secondary,
        AppearancePart.Logo to ColorGroup.Detail,
    ),
)

internal val defaultStandAppearance = ivorySage.applyTo(DeviceAppearance.DEFAULT)

internal val standThemes = listOf(
    ivorySage,
    standTheme("Chalk & ember", 0xE6E3DB, 0x343536, 0xD56A40, 0xB8B5AB, 0xEEEAE0, emptyMap()),
    standTheme(
        "Harbor blue",
        0x587A8D,
        0xECE6D7,
        0xE2AF42,
        0xB4B1A6,
        0xE6E2D7,
        mapOf(AppearancePart.FrontHolder to ColorGroup.Primary, AppearancePart.Logo to ColorGroup.Secondary, AppearancePart.Insert to ColorGroup.Secondary),
    ),
)

private fun DeviceAppearance.withExplicitStandColors(): DeviceAppearance {
    // Legacy models shared one accent across these now independent parts.
    val roles = listOf("front_holder", "rear_holder", "stand_clip", "stand_logo", "front_insert", "button_b", "power_led")
    return copy(standColors = roles.associateWith(::standColor) + standColors)
}

internal fun DeviceAppearance.assign(part: AppearancePart, group: ColorGroup?): DeviceAppearance {
    val base = withExplicitStandColors()
    val existing = colorFor(part)
    if (group == null) return base.withColor(part, existing).copy(assignments = assignments - part.name)
    val color = groupColor(group)
    return base.withColor(part, color).copy(
        palette = palette + (group.name to color),
        assignments = assignments + (part.name to group.name),
    )
}

internal fun DeviceAppearance.recolor(part: AppearancePart, color: RgbColor): DeviceAppearance {
    val group = assignments[part.name] ?: return (if (part == AppearancePart.Accent) withExplicitStandColors() else this).withColor(part, color)
    return recolorGroup(group, color)
}

internal fun DeviceAppearance.groupColor(group: ColorGroup) = palette[group.name] ?: standThemes[1].palette.getValue(group)

internal fun DeviceAppearance.recolorGroup(group: String, color: RgbColor): DeviceAppearance =
    AppearancePart.entries.filter { assignments[it.name] == group }
        .fold(withExplicitStandColors().copy(palette = palette + (group to color))) { result, member -> result.withColor(member, color) }
