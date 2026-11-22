package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.ControlBindings
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.components.product.StandPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Changes are merged into a fresh device snapshot; another part's assignment is never reset. */
internal fun changePartBinding(current: ControlBindings, part: StandPart, index: Int, value: Int): ControlBindings {
    require(index in part.bindingIndices)
    return current.changed(index, value)
}

@Composable
internal fun StandPartControls(
    part: StandPart?,
    ready: Boolean,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onEditDisplay: () -> Unit,
    onClose: () -> Unit,
    readSettings: suspend () -> ControlBindings,
    saveSettings: suspend (ControlBindings) -> ControlBindings,
    onBusyChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("stand-part-controls")) {
        if (part == null) {
            Text("LED面・ノブ・ボタンをタップして設定", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(part.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("selected-part-title"))
                TextButton(onClick = onClose) { Text("閉じる") }
            }
            if (part == StandPart.Matrix) {
                Text(stringResource(R.string.device_brightness), style = MaterialTheme.typography.labelLarge)
                ClumoSlider(value = brightness, onValueChange = onBrightnessChange, enabled = ready, modifier = Modifier.fillMaxWidth().testTag("part-brightness"))
                OutlinedButton(onClick = onEditDisplay, enabled = ready, modifier = Modifier.fillMaxWidth().testTag("part-edit-display")) {
                    Text(stringResource(R.string.stand_editor_title))
                }
            } else {
                StandPartBindings(part, ready, readSettings, saveSettings, onBusyChange)
            }
            if (!ready) Text("接続すると設定を変更できます", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun StandPartBindings(
    part: StandPart,
    ready: Boolean,
    readSettings: suspend () -> ControlBindings,
    saveSettings: suspend (ControlBindings) -> ControlBindings,
    onBusyChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val notifyBusy by rememberUpdatedState(onBusyChange)
    var saved by remember { mutableStateOf<ControlBindings?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember(part) { mutableStateOf<Int?>(null) }
    val labels = stringResource(R.string.bindings_gestures).split("|")
    val actions = stringResource(R.string.bindings_actions).split("|")
    val rotations = stringResource(R.string.bindings_rotation).split("|")
    fun run(update: ((ControlBindings) -> ControlBindings)? = null) {
        if (busy || !ready) return
        busy = true
        notifyBusy(true)
        scope.launch {
            failed = false
            try {
                val current = readSettings()
                saved = if (update == null) current else saveSettings(update(current))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saved = null
                failed = true
            } finally {
                busy = false
                notifyBusy(false)
            }
        }
    }
    LaunchedEffect(ready) {
        saved = null
        if (ready) run()
    }
    DisposableEffect(Unit) { onDispose { notifyBusy(false) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) {
            Text(stringResource(R.string.bindings_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { run() }, enabled = ready && !busy) { Text(stringResource(R.string.bindings_reload)) }
        }
        part.bindingIndices.forEach { index ->
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { editing = index }, enabled = ready && !busy && saved != null, modifier = Modifier.fillMaxWidth().testTag("part-binding-$index")) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labels[index])
                        Text(saved?.let { (if (index == 5) rotations else actions)[it.value(index)] } ?: "—")
                    }
                }
                DropdownMenu(expanded = editing == index, onDismissRequest = { editing = null }) {
                    (if (index == 5) rotations else actions).forEachIndexed { value, label ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            editing = null
                            run { current -> changePartBinding(current, part, index, value) }
                        }, modifier = Modifier.testTag("part-action-$index-$value"))
                    }
                }
            }
        }
        TextButton(onClick = {
            run { current -> part.bindingIndices.fold(current) { result, index -> result.changed(index, ControlBindings.DEFAULT.value(index)) } }
        }, enabled = ready && !busy && saved != null, modifier = Modifier.testTag("reset-part-bindings")) { Text("この部品の割り当てを初期値に戻す") }
    }
}
