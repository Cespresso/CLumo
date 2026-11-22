package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.RgbColor

enum class AppearancePart { Enclosure, ButtonA, ButtonB, Led, Panel, MatrixSurface, Windows, StandButtons, Accent, Preview, FrontHolder, RearHolder, Clip, Logo, Trim, Insert, PowerLens }

enum class AppearancePresetName {
    Sage,
    Coral,
    MistBlue,
    Lavender,
    Mustard,
    DustyRose,
    Sand,
    Stone,
    White,
    Red,
    Blue,
    Green,
    Orange,
    Yellow,
    Purple,
    WarmWhite,
}

data class AppearancePreset(
    val name: AppearancePresetName,
    val color: RgbColor,
)

val PHYSICAL_PART_PRESETS = listOf(
    preset(AppearancePresetName.Sage, "#7E9E7C"),
    preset(AppearancePresetName.Coral, "#E8907E"),
    preset(AppearancePresetName.MistBlue, "#8CAAB5"),
    preset(AppearancePresetName.Lavender, "#A99ABC"),
    preset(AppearancePresetName.Mustard, "#D1AD66"),
    preset(AppearancePresetName.DustyRose, "#C58F9A"),
    preset(AppearancePresetName.Sand, "#A8927C"),
    preset(AppearancePresetName.Stone, "#969A98"),
    preset(AppearancePresetName.White, "#FFFFFF"),
)

val LED_PRESETS = listOf(
    preset(AppearancePresetName.Red, "#E85D53"),
    preset(AppearancePresetName.Blue, "#538BC5"),
    preset(AppearancePresetName.Green, "#54A36A"),
    preset(AppearancePresetName.Orange, "#F0A35E"),
    preset(AppearancePresetName.Yellow, "#D7B84B"),
    preset(AppearancePresetName.Purple, "#A276B5"),
    preset(AppearancePresetName.WarmWhite, "#FFF4DC"),
)

private fun preset(name: AppearancePresetName, hex: String): AppearancePreset = AppearancePreset(name, requireNotNull(RgbColor.parseOrNull(hex)))

internal fun AppearancePart.modelRoles(): List<String> =
    when (this) {
        AppearancePart.Enclosure -> listOf("enclosure")
        AppearancePart.Accent -> listOf("stand_knob")
        AppearancePart.StandButtons -> listOf("stand_left", "stand_right")
        AppearancePart.Windows -> listOf("volume_2", "volume_1", "volume_3", "window")
        AppearancePart.MatrixSurface, AppearancePart.Preview, AppearancePart.Led -> listOf("matrix_surface")
        AppearancePart.Panel -> listOf("panel")
        AppearancePart.FrontHolder -> listOf("front_holder")
        AppearancePart.RearHolder -> listOf("rear_holder")
        AppearancePart.Clip -> listOf("stand_clip")
        AppearancePart.Logo -> listOf("stand_logo")
        AppearancePart.Insert -> listOf("front_insert")
        AppearancePart.Trim -> listOf("button_b")
        AppearancePart.PowerLens -> listOf("power_led")
        AppearancePart.ButtonA -> listOf("button_a")
        AppearancePart.ButtonB -> listOf("button_b")
    }
internal fun editableParts(stand: Boolean) =
    if (stand) {
        listOf(
            AppearancePart.Enclosure, AppearancePart.Accent, AppearancePart.StandButtons, AppearancePart.Panel,
            AppearancePart.Windows, AppearancePart.MatrixSurface, AppearancePart.Preview, AppearancePart.FrontHolder, AppearancePart.RearHolder, AppearancePart.Clip, AppearancePart.Logo, AppearancePart.Trim, AppearancePart.Insert, AppearancePart.PowerLens,
        )
    } else {
        listOf(AppearancePart.Enclosure, AppearancePart.ButtonA, AppearancePart.ButtonB, AppearancePart.Led)
    }
