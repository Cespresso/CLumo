package io.github.cespresso.clumo.ui.appearance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import io.github.cespresso.clumo.ui.components.HeadphoneStandDevice
import io.github.cespresso.clumo.ui.components.ScreenHeader
import io.github.cespresso.clumo.ui.components.product.STAND_PREVIEW_HEIGHT
import io.github.cespresso.clumo.ui.components.product.STAND_PREVIEW_WIDTH_WIDE
import io.github.cespresso.clumo.ui.components.toComposeColor

@Composable
internal fun StandAppearanceEditor(
    vm: DeviceAppearanceViewModel,
    appearance: DeviceAppearance,
    spatial: Boolean,
    part: AppearancePart?,
    group: ColorGroup?,
    preview: StandTheme?,
    selectPart: (AppearancePart) -> Unit,
    selectGroup: (ColorGroup) -> Unit,
    previewTheme: (StandTheme?) -> Unit,
    back: () -> Unit,
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val physical = part != null && part != AppearancePart.Preview
    val individual = part != null && appearance.assignments[part.name] == null
    val members = editableParts(true).filter { group != null && appearance.assignments[it.name] == group.name }
    BackHandler(part != null || group != null || preview != null, onBack = back)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        if (!spatial) {
            Box(
                (if (wide) Modifier.fillMaxWidth(STAND_PREVIEW_WIDTH_WIDE).fillMaxHeight() else Modifier.fillMaxWidth().fillMaxHeight(STAND_PREVIEW_HEIGHT))
                    .background(Color(0xFFD9D4CB)),
                contentAlignment = Alignment.Center,
            ) {
                HeadphoneStandDevice(
                    if (part == AppearancePart.Preview) 0x00183C7E7EFF6600L else 0L,
                    128.dp,
                    appearance,
                )
            }
        }
        ScreenHeader("Appearance", back)
        Surface(
            modifier = if (wide) {
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(1f - STAND_PREVIEW_WIDTH_WIDE).fillMaxHeight()
            } else {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(1f - STAND_PREVIEW_HEIGHT)
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFFFAF6F1),
        ) {
            key(part, group) {
                Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + (if (wide) WindowInsetsSides.Top else WindowInsetsSides.Bottom)))
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        group?.let { "${it.name} color" } ?: part?.spatialLabel() ?: "Theme presets",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (ui.saveFailed) Text(stringResource(R.string.appearance_save_error), color = MaterialTheme.colorScheme.error)
                    if (part == null && group == null) {
                        ThemeCards(preview ?: standThemes.firstOrNull { it.matches(appearance) }, previewTheme)
                        Button(
                            onClick = {
                                preview?.let(vm::applyTheme)
                                previewTheme(null)
                            },
                            enabled = preview != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Apply preset") }
                        if (preview != null) {
                            TextButton(onClick = { previewTheme(null) }) { Text("Cancel preview") }
                        } else {
                            Text("Customize palette", style = MaterialTheme.typography.titleMedium)
                            ColorGroup.entries.forEach { slot ->
                                ColorRow(slot.name, appearance.groupColor(slot), null) { selectGroup(slot) }
                            }
                            Text("Parts", style = MaterialTheme.typography.titleMedium)
                            editableParts(true).forEach { item ->
                                ColorRow(item.spatialLabel(), appearance.colorFor(item), null) { selectPart(item) }
                            }
                            TextButton(onClick = vm::reset) { Text(stringResource(R.string.appearance_reset)) }
                        }
                    } else {
                        if (group != null) Text(members.joinToString(" · ") { it.spatialLabel() }.ifEmpty { "No parts assigned" })
                        if (physical) {
                            Text("Color source", style = MaterialTheme.typography.titleMedium)
                            Column(Modifier.selectableGroup()) {
                                ColorGroup.entries.forEach { slot ->
                                    ColorRow(slot.name, appearance.groupColor(slot), appearance.assignments[part.name] == slot.name) {
                                        vm.assignGroup(part, slot)
                                    }
                                }
                                HorizontalDivider()
                                ColorRow("Individual", appearance.colorFor(part), individual) { vm.assignGroup(part, null) }
                            }
                        }
                        if (group != null || individual || part == AppearancePart.Preview) {
                            val current = group?.let(appearance::groupColor) ?: appearance.colorFor(requireNotNull(part))
                            val editColor: (RgbColor) -> Unit = { color ->
                                if (group != null) vm.updateGroup(group, color) else vm.onColorSelected(requireNotNull(part), color)
                            }
                            StandSwatches(current, part, group, editColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCards(selected: StandTheme?, choose: (StandTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        standThemes.forEach { theme ->
            val active = selected == theme
            OutlinedCard(
                onClick = { choose(theme) },
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(
                    if (active) 2.dp else 1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PresetSwatch(theme)
                    Text(theme.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PresetSwatch(theme: StandTheme) {
    fun partColor(part: AppearancePart) = theme.palette.getValue(theme.parts.getValue(part)).toComposeColor()
    val body = partColor(AppearancePart.Enclosure)
    val logo = partColor(AppearancePart.Logo)
    val knob = partColor(AppearancePart.Accent)
    val holder = partColor(AppearancePart.FrontHolder)
    Canvas(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(8.dp)).background(body)) {
        val unit = size.minDimension
        val x = unit * .18f
        val y = unit * .18f
        val mark = Path().apply {
            moveTo(x, y + unit * .25f)
            lineTo(x, y + unit * .08f)
            quadraticTo(x, y, x + unit * .08f, y)
            lineTo(x + unit * .25f, y)
        }
        drawPath(mark, logo, style = Stroke(unit * .055f, cap = StrokeCap.Round))
        drawLine(
            holder,
            Offset(x, size.height * .80f),
            Offset(size.width * .48f, size.height * .80f),
            strokeWidth = unit * .075f,
            cap = StrokeCap.Round,
        )
        drawCircle(knob, unit * .115f, Offset(size.width - unit * .23f, size.height * .77f))
    }
}

@Composable
private fun ColorRow(label: String, color: RgbColor, checked: Boolean?, click: () -> Unit) {
    val action = if (checked == null) {
        Modifier.clickable(onClick = click)
    } else {
        Modifier.selectable(checked, role = Role.RadioButton, onClick = click)
    }
    Row(
        Modifier.fillMaxWidth().then(action).heightIn(min = 48.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(color.toComposeColor(), CircleShape))
        Text(label, Modifier.weight(1f))
        if (checked == null) Text("›") else RadioButton(selected = checked, onClick = null)
    }
}

@Composable
private fun StandSwatches(current: RgbColor, part: AppearancePart?, group: ColorGroup?, change: (RgbColor) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    val presets = if (part == AppearancePart.Preview) LED_PRESETS else PHYSICAL_PART_PRESETS
    val colors = (presets.map { it.color } + current).distinct()
    colors.chunked(5).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { rgb ->
                val color = rgb.toComposeColor()
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(color)
                        .selectable(rgb == current, onClick = { change(rgb) }).semantics { contentDescription = rgb.toHex() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (rgb == current) Text("✓", color = if (color.luminance() > .4f) Color.Black else Color.White)
                }
            }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    OutlinedButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Text("Custom color") }
    if (custom) {
        CustomColorDialog(
            part ?: AppearancePart.Enclosure,
            current,
            onConfirm = {
                custom = false
                change(it)
            },
            onDismiss = { custom = false },
            title = (group?.name ?: part?.spatialLabel().orEmpty()) + " color",
        )
    }
}
