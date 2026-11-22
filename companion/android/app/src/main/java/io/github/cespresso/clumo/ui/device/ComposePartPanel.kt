package io.github.cespresso.clumo.ui.device

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.ui.components.product.FloorSettingsState
import kotlin.math.roundToInt

/** Settings occupy the lower half; the shared scene camera reserves the upper half. */
@Composable
internal fun AnchoredPartControls(
    state: FloorSettingsState,
    accent: Color,
    modifier: Modifier = Modifier,
    displayControl: @Composable () -> Unit = {},
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(state.snapshot.part, state.snapshot.choosing, state.cameraSettled) {
        reveal.snapTo(0f)
        if (state.cameraSettled) reveal.animateTo(1f, tween(240))
    }
    BoxWithConstraints(modifier) {
        val top = maxHeight * .52f
        ComposePartPanel(
            state,
            accent,
            reveal.value,
            Modifier.padding(top = top).fillMaxSize(),
            displayControl,
        )
    }
}

@Composable
private fun ComposePartPanel(state: FloorSettingsState, accent: Color, progress: Float, modifier: Modifier = Modifier, displayControl: @Composable () -> Unit) {
    val snapshot = state.snapshot
    val ink = Color(0xFF30342F)
    val muted = Color(0xFF575B54)
    fun Modifier.reveal(index: Int) =
        graphicsLayer {
            val amount = (progress * 1.8f - index * .10f).coerceIn(0f, 1f)
            alpha = amount
            translationY = (1f - amount) * 12.dp.toPx()
        }
    CompositionLocalProvider(LocalContentColor provides ink) {
        key(snapshot.part, snapshot.bindingIndex) {
            Column(
                modifier
                    // Keep the scroll viewport edge-to-edge. Insets belong to the content,
                    // so rows can pass behind the navigation bar while scrolling and the
                    // final row can still be brought fully above it at the end.
                    .then(if (snapshot.choosing) Modifier.selectableGroup() else Modifier)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).reveal(0), verticalAlignment = Alignment.CenterVertically) {
                    Text(snapshot.title, modifier = Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    TextButton(
                        onClick = { state.onAction("close", null) },
                        enabled = !snapshot.busy && progress > .8f,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = muted),
                    ) {
                        Text(if (snapshot.choosing) "‹" else "×", fontSize = 24.sp)
                    }
                }
                if (snapshot.part != io.github.cespresso.clumo.ui.components.product.StandPart.Matrix) {
                    Text(
                        if (snapshot.choosing) "Choose an action" else "Control assignments",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier.reveal(0),
                    )
                }
                snapshot.saveStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (snapshot.saveFailed) MaterialTheme.colorScheme.error else muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if ((!snapshot.enabled || snapshot.busy) && snapshot.saveStatus == null) {
                    Text(snapshot.status, modifier = Modifier.reveal(1), style = MaterialTheme.typography.bodySmall)
                    if (!snapshot.busy) TextButton(onClick = { state.onAction("retry", null) }) { Text("Reconnect / Retry") }
                }
                if (snapshot.part == io.github.cespresso.clumo.ui.components.product.StandPart.Matrix && !snapshot.choosing) {
                    Box(Modifier.reveal(1)) { displayControl() }
                    Spacer(Modifier.height(12.dp))
                }
                snapshot.brightness?.let { brightness ->
                    Column(Modifier.reveal(1)) {
                        val level = Brightness.toLevel(brightness)
                        Text("Brightness · ${level + 1} / ${Brightness.MAX_LEVEL + 1}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = (level + 1).toFloat(),
                            onValueChange = { state.onAction("brightness", Brightness.toPercent(it.roundToInt() - 1)) },
                            valueRange = 1f..(Brightness.MAX_LEVEL + 1).toFloat(),
                            steps = Brightness.MAX_LEVEL - 1,
                            enabled = snapshot.enabled && progress > .9f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = ink.copy(alpha = .12f)),
                        )
                    }
                }
                snapshot.items.sortedBy { if (it.id == "binding:5") -1 else 0 }.forEachIndexed { index, item ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().reveal(index + 1)
                            .then(
                                if (snapshot.choosing) {
                                    Modifier.selectable(
                                        selected = item.selected,
                                        enabled = snapshot.enabled && progress > .95f,
                                        role = Role.RadioButton,
                                        onClick = { state.onAction(item.id, null) },
                                    )
                                } else {
                                    Modifier.clickable(enabled = snapshot.enabled && progress > .95f, role = Role.Button) { state.onAction(item.id, null) }
                                },
                            ),
                        overlineContent = if (item.value.isNotEmpty()) ({ Text(item.title) }) else null,
                        headlineContent = { Text(item.value.ifEmpty { item.title }) },
                        leadingContent = item.icon?.let { icon -> { AssignmentIcon(icon) } },
                        trailingContent = {
                            if (snapshot.choosing) {
                                RadioButton(
                                    selected = item.selected,
                                    onClick = null,
                                    enabled = snapshot.enabled && progress > .95f,
                                    colors = RadioButtonDefaults.colors(selectedColor = accent),
                                )
                            } else {
                                Icon(painterResource(R.drawable.ic_action_chevron_right), contentDescription = null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = ink.copy(alpha = .13f))
                }
            }
        }
    }
}

@Composable
private fun AssignmentIcon(name: String) {
    val resource = when (name) {
        "volume" -> R.drawable.ic_action_volume_up
        "play" -> R.drawable.ic_action_play_arrow
        "previous" -> R.drawable.ic_action_skip_previous
        "next" -> R.drawable.ic_action_skip_next
        "mute" -> R.drawable.ic_action_volume_off
        "voice" -> R.drawable.ic_action_mic
        "forward" -> R.drawable.ic_action_fast_forward
        "rewind" -> R.drawable.ic_action_fast_rewind
        "display" -> R.drawable.ic_action_grid_on
        else -> R.drawable.ic_action_remove
    }
    Icon(painterResource(resource), contentDescription = null)
}
