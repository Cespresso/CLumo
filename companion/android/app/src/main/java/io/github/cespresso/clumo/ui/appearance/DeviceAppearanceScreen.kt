package io.github.cespresso.clumo.ui.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import io.github.cespresso.clumo.service.DeviceHubService
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.FaceBits
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.toComposeColor
import io.github.cespresso.clumo.ui.settings.ScreenHeader
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.launch

private const val PREVIEW_BITS =
    "0000000001100110111111111111111101111110001111000001100000000000"

@Composable
fun DeviceAppearanceScreen(
    service: DeviceHubService,
    deviceId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appearances by service.preferences.deviceAppearances.collectAsState(initial = emptyMap())
    val aliases by service.preferences.aliases.collectAsState(initial = emptyMap())
    val persisted = appearances[deviceId] ?: DeviceAppearance.DEFAULT
    val deviceName = aliases[deviceId] ?: service.repository.get(deviceId)?.fallbackName ?: "CLumo"
    var appearance by remember(deviceId) { mutableStateOf(persisted) }
    var saveFailed by remember(deviceId) { mutableStateOf(false) }
    var editingPart by remember { mutableStateOf<AppearancePart?>(null) }

    LaunchedEffect(persisted) {
        appearance = persisted
        saveFailed = false
    }

    fun persist(next: DeviceAppearance) {
        appearance = next
        saveFailed = false
        scope.launch {
            runCatching { service.preferences.setDeviceAppearance(deviceId, next) }
                .onFailure {
                    appearance = persisted
                    saveFailed = true
                }
        }
    }

    fun reset() {
        appearance = DeviceAppearance.DEFAULT
        saveFailed = false
        scope.launch {
            runCatching { service.preferences.resetDeviceAppearance(deviceId) }
                .onFailure {
                    appearance = persisted
                    saveFailed = true
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.appearance_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    size = 160.dp,
                    appearance = appearance,
                    shadowElevation = 10.dp,
                )
                Text(
                    text = stringResource(R.string.appearance_preview_label, deviceName),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Muted,
                )
            }

            if (saveFailed) {
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

            AppearanceColorSection(
                part = AppearancePart.Enclosure,
                current = appearance.enclosureColor,
                presets = PHYSICAL_PART_PRESETS,
                onSelect = { persist(appearance.withColor(AppearancePart.Enclosure, it)) },
                onCustom = { editingPart = AppearancePart.Enclosure },
            )
            AppearanceColorSection(
                part = AppearancePart.ButtonA,
                current = appearance.buttonAColor,
                presets = PHYSICAL_PART_PRESETS,
                onSelect = { persist(appearance.withColor(AppearancePart.ButtonA, it)) },
                onCustom = { editingPart = AppearancePart.ButtonA },
            )
            AppearanceColorSection(
                part = AppearancePart.ButtonB,
                current = appearance.buttonBColor,
                presets = PHYSICAL_PART_PRESETS,
                onSelect = { persist(appearance.withColor(AppearancePart.ButtonB, it)) },
                onCustom = { editingPart = AppearancePart.ButtonB },
            )
            AppearanceColorSection(
                part = AppearancePart.Led,
                current = appearance.ledColor,
                presets = LED_PRESETS,
                onSelect = { persist(appearance.withColor(AppearancePart.Led, it)) },
                onCustom = { editingPart = AppearancePart.Led },
            )
            OutlinePillButton(
                text = stringResource(R.string.appearance_reset),
                onClick = ::reset,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    editingPart?.let { part ->
        CustomColorDialog(
            part = part,
            initialColor = appearance.colorFor(part),
            onConfirm = { selected ->
                editingPart = null
                persist(appearance.withColor(part, selected))
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
private fun CustomColorDialog(
    part: AppearancePart,
    initialColor: RgbColor,
    onConfirm: (RgbColor) -> Unit,
    onDismiss: () -> Unit,
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
                    text = stringResource(R.string.appearance_picker_title, stringResource(part.stringRes())),
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
                    colors = ButtonDefaults.buttonColors(containerColor = ClumoColors.Coral),
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

private fun DeviceAppearance.colorFor(part: AppearancePart): RgbColor = when (part) {
    AppearancePart.Enclosure -> enclosureColor
    AppearancePart.ButtonA -> buttonAColor
    AppearancePart.ButtonB -> buttonBColor
    AppearancePart.Led -> ledColor
}

private fun DeviceAppearance.withColor(part: AppearancePart, color: RgbColor): DeviceAppearance =
    when (part) {
        AppearancePart.Enclosure -> copy(enclosureColor = color)
        AppearancePart.ButtonA -> copy(buttonAColor = color)
        AppearancePart.ButtonB -> copy(buttonBColor = color)
        AppearancePart.Led -> copy(ledColor = color)
    }

@StringRes
private fun AppearancePart.stringRes(): Int = when (this) {
    AppearancePart.Enclosure -> R.string.appearance_enclosure
    AppearancePart.ButtonA -> R.string.appearance_button_a
    AppearancePart.ButtonB -> R.string.appearance_button_b
    AppearancePart.Led -> R.string.appearance_led
}

@StringRes
private fun AppearancePresetName.stringRes(): Int = when (this) {
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
