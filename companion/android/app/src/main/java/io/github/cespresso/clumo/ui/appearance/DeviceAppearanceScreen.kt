package io.github.cespresso.clumo.ui.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.RgbColor
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.ScreenHeader
import io.github.cespresso.clumo.ui.components.toComposeColor
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

private const val PREVIEW_BITS =
    "0000000001100110111111111111111101111110001111000001100000000000"

@Composable
fun DeviceAppearanceScreen(
    viewModel: DeviceAppearanceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spatialPart: AppearancePart? = null,
    onPart: (AppearancePart) -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = ui.appearance
    var editingPart by remember { mutableStateOf<AppearancePart?>(null) }

    if (spatialPart != null) {
        SpatialAppearancePanel(
            spatialPart, appearance, viewModel::applyTheme, { viewModel.assignGroup(spatialPart, it) },
            ui.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND, ui.saveFailed,
            onBack, onPart, { viewModel.onColorSelected(spatialPart, it) }, { editingPart = spatialPart }, viewModel::reset,
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.appearance_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(ClumoColors.White)
                        .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClumoDevice(
                        bits = FaceBits.fromBitsString(PREVIEW_BITS),
                        kind = ui.kind,
                        size = 160.dp,
                        appearance = appearance,
                        shadowElevation = 10.dp,
                    )
                    Text(
                        text = stringResource(R.string.appearance_preview_label, ui.deviceName),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.Muted,
                    )
                }

                if (ui.saveFailed) {
                    Text(
                        text = stringResource(R.string.appearance_save_error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ClumoColors.ErrorBg)
                            .border(1.dp, ClumoColors.ErrorBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.ErrorText,
                    )
                }

                val parts = if (ui.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) {
                    listOf(AppearancePart.Enclosure, AppearancePart.Panel, AppearancePart.MatrixSurface, AppearancePart.StandButtons, AppearancePart.Windows, AppearancePart.Accent, AppearancePart.Preview)
                } else {
                    listOf(AppearancePart.Enclosure, AppearancePart.ButtonA, AppearancePart.ButtonB, AppearancePart.Led)
                }
                for (part in parts) {
                    AppearanceColorSection(
                        part = part,
                        current = appearance.colorFor(part),
                        presets = if (part == AppearancePart.Led || part == AppearancePart.Preview) LED_PRESETS else PHYSICAL_PART_PRESETS,
                        onSelect = { viewModel.onColorSelected(part, it) },
                        onCustom = { editingPart = part },
                    )
                }
                OutlinePillButton(
                    text = stringResource(R.string.appearance_reset),
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    editingPart?.let { part ->
        CustomColorDialog(
            part = part,
            initialColor = appearance.colorFor(part),
            onConfirm = { selected ->
                editingPart = null
                viewModel.onColorSelected(part, selected)
            },
            onDismiss = { editingPart = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceColorSection(
    part: AppearancePart,
    current: RgbColor,
    presets: List<AppearancePreset>,
    onSelect: (RgbColor) -> Unit,
    onCustom: () -> Unit,
) {
    val partName = stringResource(part.stringRes())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = partName,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(current.toComposeColor())
                        .border(1.dp, ClumoColors.OutlineBorder, CircleShape),
                )
                Text(
                    text = current.toHex(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Muted,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                val selected = current == preset.color
                val presetName = stringResource(preset.name.stringRes())
                val selectedText = if (selected) {
                    ", ${stringResource(R.string.appearance_selected)}"
                } else {
                    ""
                }
                val swatchDescription = stringResource(
                    R.string.appearance_swatch_description,
                    partName,
                    presetName,
                ) + selectedText
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            this.selected = selected
                            contentDescription = swatchDescription
                        }
                        .clickable { onSelect(preset.color) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(preset.color.toComposeColor())
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) ClumoColors.Text else ClumoColors.OutlineBorder,
                                shape = CircleShape,
                            ),
                    )
                }
            }
            val customSelected = presets.none { it.color == current }
            val customDescription = "$partName, ${stringResource(R.string.appearance_custom_color)}" +
                if (customSelected) ", ${stringResource(R.string.appearance_selected)}" else ""
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        this.selected = customSelected
                        contentDescription = customDescription
                    }
                    .clickable(onClick = onCustom),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ClumoColors.White)
                        .border(
                            if (customSelected) 3.dp else 1.5.dp,
                            if (customSelected) ClumoColors.Text else ClumoColors.OutlineBorder,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClumoColors.Text,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CustomColorDialog(
    part: AppearancePart,
    initialColor: RgbColor,
    onConfirm: (RgbColor) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.appearance_picker_title, stringResource(part.stringRes())),
) {
    var picker by remember(initialColor) { mutableStateOf(ColorPickerState(initialColor)) }
    val hsv = picker.draftColor.toHsv()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(ClumoColors.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Text,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(picker.draftColor.toComposeColor())
                        .border(1.5.dp, ClumoColors.OutlineBorder, RoundedCornerShape(13.dp)),
                )
            }
            SaturationValuePicker(
                hue = hsv[0],
                saturation = hsv[1],
                value = hsv[2],
                onChange = { saturation, value ->
                    picker = picker.withHsv(hsv[0], saturation, value)
                },
            )
            HuePicker(
                hue = hsv[0],
                onChange = { hue -> picker = picker.withHsv(hue, hsv[1], hsv[2]) },
            )
            OutlinedTextField(
                value = picker.hexInput,
                onValueChange = { picker = picker.withHexInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.appearance_hex_label)) },
                isError = !picker.canConfirm,
                supportingText = if (!picker.canConfirm) {
                    { Text(stringResource(R.string.appearance_hex_error)) }
                } else {
                    null
                },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                Button(
                    onClick = { picker.confirmedColorOrNull()?.let(onConfirm) },
                    enabled = picker.canConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalClumoAccents.current.cta,
                        contentColor = LocalClumoAccents.current.onCta,
                    ),
                ) {
                    Text(stringResource(R.string.appearance_use_color))
                }
            }
        }
    }
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(hue) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(position: Offset) {
                        onChange(
                            (position.x / size.width).coerceIn(0f, 1f),
                            (1f - position.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    update(down.position)
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.White, Color.hsv(hue, 1f, 1f)),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun HuePicker(hue: Float, onChange: (Float) -> Unit) {
    val colors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(x: Float) = onChange((x / size.width).coerceIn(0f, 1f) * 360f)
                    update(down.position.x)
                    drag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            cornerRadius = CornerRadius(size.height / 2f),
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset((hue / 360f) * size.width, size.height / 2f),
            style = Stroke(width = 4.dp.toPx()),
        )
    }
}

internal fun DeviceAppearance.colorFor(part: AppearancePart): RgbColor =
    when (part) {
        AppearancePart.Enclosure -> enclosureColor
        AppearancePart.ButtonA -> buttonAColor
        AppearancePart.ButtonB -> buttonBColor
        AppearancePart.FrontHolder, AppearancePart.RearHolder, AppearancePart.Clip, AppearancePart.Logo, AppearancePart.Trim, AppearancePart.Insert, AppearancePart.PowerLens -> standColor(part.modelRoles().first())
        AppearancePart.Led -> ledColor
        AppearancePart.Panel -> panelColor
        AppearancePart.MatrixSurface -> matrixSurfaceColor
        AppearancePart.Windows -> windowColor
        AppearancePart.StandButtons -> buttonAColor
        AppearancePart.Accent -> buttonBColor
        AppearancePart.Preview -> ledColor
    }

internal fun DeviceAppearance.withColor(part: AppearancePart, color: RgbColor): DeviceAppearance =
    when (part) {
        AppearancePart.Enclosure -> copy(enclosureColor = color)
        AppearancePart.ButtonA -> copy(buttonAColor = color)
        AppearancePart.ButtonB -> copy(buttonBColor = color)
        AppearancePart.FrontHolder, AppearancePart.RearHolder, AppearancePart.Clip, AppearancePart.Logo, AppearancePart.Trim, AppearancePart.Insert, AppearancePart.PowerLens -> copy(standColors = standColors + (part.modelRoles().first() to color))
        AppearancePart.Led -> copy(ledColor = color)
        AppearancePart.Panel -> copy(panelColor = color)
        AppearancePart.MatrixSurface -> copy(matrixSurfaceColor = color)
        AppearancePart.Windows -> copy(windowColor = color)
        AppearancePart.StandButtons -> copy(buttonAColor = color)
        AppearancePart.Accent -> copy(buttonBColor = color)
        AppearancePart.Preview -> copy(ledColor = color)
    }

@StringRes
private fun AppearancePart.stringRes(): Int =
    when (this) {
        AppearancePart.Enclosure -> R.string.appearance_enclosure
        AppearancePart.ButtonA -> R.string.appearance_button_a
        AppearancePart.ButtonB -> R.string.appearance_button_b
        AppearancePart.Led -> R.string.appearance_led
        AppearancePart.Panel -> R.string.appearance_stand_panel
        AppearancePart.MatrixSurface -> R.string.appearance_stand_matrixsurface
        AppearancePart.Windows -> R.string.appearance_stand_windows
        AppearancePart.StandButtons -> R.string.appearance_stand_standbuttons
        AppearancePart.FrontHolder -> R.string.appearance_front_holder
        AppearancePart.RearHolder -> R.string.appearance_rear_holder
        AppearancePart.Clip -> R.string.appearance_stand_clip
        AppearancePart.Logo -> R.string.appearance_stand_logo
        AppearancePart.Insert -> R.string.appearance_front_insert
        AppearancePart.Trim -> R.string.appearance_front_trim
        AppearancePart.PowerLens -> R.string.appearance_power_led
        AppearancePart.Accent -> R.string.appearance_stand_accent
        AppearancePart.Preview -> R.string.appearance_stand_preview
    }

@StringRes
private fun AppearancePresetName.stringRes(): Int =
    when (this) {
        AppearancePresetName.Sage -> R.string.appearance_preset_sage
        AppearancePresetName.Coral -> R.string.appearance_preset_coral
        AppearancePresetName.MistBlue -> R.string.appearance_preset_mist_blue
        AppearancePresetName.Lavender -> R.string.appearance_preset_lavender
        AppearancePresetName.Mustard -> R.string.appearance_preset_mustard
        AppearancePresetName.DustyRose -> R.string.appearance_preset_dusty_rose
        AppearancePresetName.Sand -> R.string.appearance_preset_sand
        AppearancePresetName.Stone -> R.string.appearance_preset_stone
        AppearancePresetName.White -> R.string.appearance_preset_white
        AppearancePresetName.Red -> R.string.appearance_preset_red
        AppearancePresetName.Blue -> R.string.appearance_preset_blue
        AppearancePresetName.Green -> R.string.appearance_preset_green
        AppearancePresetName.Orange -> R.string.appearance_preset_orange
        AppearancePresetName.Yellow -> R.string.appearance_preset_yellow
        AppearancePresetName.Purple -> R.string.appearance_preset_purple
        AppearancePresetName.WarmWhite -> R.string.appearance_preset_warm_white
    }

internal fun AppearancePart.spatialLabel() =
    when (this) {
        AppearancePart.FrontHolder -> "Front holder"
        AppearancePart.RearHolder -> "Rear holder"
        AppearancePart.Clip -> "Headphone clip"
        AppearancePart.Logo -> "Logo"
        AppearancePart.Insert -> "Front insert"
        AppearancePart.Trim -> "Front trim"
        AppearancePart.PowerLens -> "Power lens"
        AppearancePart.Accent -> "Knob"
        AppearancePart.Enclosure -> "Body"
        AppearancePart.StandButtons -> "Buttons"
        AppearancePart.Panel -> "Panel"
        AppearancePart.MatrixSurface -> "Diffuser"
        AppearancePart.Windows -> "LED windows"
        AppearancePart.Preview, AppearancePart.Led -> "LED"
        AppearancePart.ButtonA -> "Button A"
        AppearancePart.ButtonB -> "Button B"
    }

private val studioColors = listOf(
    "Ivory" to 0xEAE4D8,
    "Sage" to 0x7E9E7C,
    "Slate blue" to 0x748897,
    "Terracotta" to 0xC47749,
    "Mustard" to 0xD1AD66,
    "Charcoal" to 0x535553,
)

@Composable
private fun SpatialAppearancePanel(
    part: AppearancePart,
    appearance: DeviceAppearance,
    applyTheme: (StandTheme) -> Unit,
    assignGroup: (ColorGroup?) -> Unit,
    stand: Boolean,
    saveFailed: Boolean,
    back: () -> Unit,
    selectPart: (AppearancePart) -> Unit,
    selectColor: (RgbColor) -> Unit,
    custom: () -> Unit,
    reset: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var themesOpen by remember { mutableStateOf(false) }
    var groupsOpen by remember(part) { mutableStateOf(false) }
    val current = appearance.colorFor(part)
    val group = appearance.assignments[part.name]
    val palette = if (part == AppearancePart.Led || part == AppearancePart.Preview) {
        LED_PRESETS.map { stringResource(it.name.stringRes()) to it.color.value }
    } else {
        studioColors
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Appearance", onBack = back) {
            if (stand) {
                Box {
                    TextButton(onClick = { themesOpen = true }) {
                        Text(standThemes.firstOrNull { it.matches(appearance) }?.name ?: "Themes")
                    }
                    DropdownMenu(themesOpen, { themesOpen = false }) {
                        standThemes.forEach { theme ->
                            DropdownMenuItem(text = { Text(theme.name) }, leadingIcon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    theme.palette.values.take(3).forEach {
                                        Box(Modifier.size(12.dp).background(it.toComposeColor(), CircleShape))
                                    }
                                }
                            }, onClick = {
                                themesOpen = false
                                applyTheme(theme)
                            })
                        }
                    }
                }
            }
        }
        val wide = maxWidth > maxHeight
        Column(
            Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                .fillMaxWidth(if (wide) .42f else 1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) {
                    TextButton(onClick = { menu = true }) {
                        Text(
                            part.spatialLabel() + " color  ▾",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClumoColors.Text,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        editableParts(stand).forEach { option ->
                            DropdownMenuItem(text = { Text(option.spatialLabel()) }, onClick = {
                                menu = false
                                selectPart(option)
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.appearance_reset)) }, onClick = {
                            menu = false
                            reset()
                        })
                    }
                }
                if (stand) {
                    Box {
                        TextButton(onClick = { groupsOpen = true }) { Text((group ?: "Individual") + "  ▾") }
                        DropdownMenu(groupsOpen, { groupsOpen = false }) {
                            DropdownMenuItem(text = { Text("Individual color") }, onClick = {
                                groupsOpen = false
                                assignGroup(null)
                            })
                            ColorGroup.entries.forEach { slot ->
                                DropdownMenuItem(text = { Text(slot.name) }, leadingIcon = {
                                    Box(Modifier.size(18.dp).background((appearance.palette[slot.name] ?: current).toComposeColor(), CircleShape))
                                }, onClick = {
                                    groupsOpen = false
                                    assignGroup(slot)
                                })
                            }
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                palette.forEach { (label, rgb) ->
                    val active = current.value == rgb
                    val color = RgbColor.of(rgb).toComposeColor()
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { selectColor(RgbColor.of(rgb)) }
                            .semantics {
                                contentDescription = label
                                selected = active
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(42.dp).border(
                                if (active) 2.dp else 1.dp,
                                if (active) ClumoColors.Text else ClumoColors.OutlineBorder,
                                CircleShape,
                            )
                                .padding(if (active) 3.dp else 1.dp).background(color, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) Text("✓", color = if (color.luminance() > .5f) Color.Black else Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(palette.firstOrNull { it.second == current.value }?.first ?: current.toHex(), color = ClumoColors.Text)
                TextButton(onClick = custom) { Text(stringResource(R.string.appearance_custom_color)) }
            }
            if (saveFailed) Text(stringResource(R.string.appearance_save_error), color = ClumoColors.ErrorText)
        }
    }
}
