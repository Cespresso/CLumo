package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.ControlBindings
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.ScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StandBindingsEditor(
    appearance: DeviceAppearance,
    readSettings: suspend () -> ControlBindings,
    saveSettings: suspend (ControlBindings) -> ControlBindings,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<ControlBindings?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var group by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var reset by remember { mutableStateOf(false) }
    val labels = stringResource(R.string.bindings_gestures).split("|")
    val actions = stringResource(R.string.bindings_actions).split("|")
    val rotation = stringResource(R.string.bindings_rotation).split("|")
    val groups = stringResource(R.string.bindings_parts).split("|")
    fun load() {
        scope.launch {
            busy = true
            failed = false
            try {
                saved = readSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The device may have accepted a write even when its read-back failed.
                // Require a fresh read before another whole-packet edit.
                saved = null
                failed = true
            } finally {
                busy = false
            }
        }
    }
    fun save(next: ControlBindings) {
        scope.launch {
            busy = true
            failed = false
            try {
                saved = saveSettings(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The device may have accepted a write even when its read-back failed.
                // Require a fresh read before another whole-packet edit.
                saved = null
                failed = true
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ClumoColors.Background) {
            Column {
                ScreenHeader(title = stringResource(R.string.bindings_title), onBack = { if (!busy) onClose() })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(260.dp).align(Alignment.CenterHorizontally)) {
                        ClumoDevice(bits = 0L, size = 260.dp, appearance = appearance, kind = io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND)
                        for ((i, x) in listOf(0 to 53, 1 to 108, 2 to 174)) {
                            Box(Modifier.offset(x.dp, 174.dp).size(if (i == 2) 64.dp else 48.dp).semantics { contentDescription = groups[i] }.clickable { group = i })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        groups.forEachIndexed { i, label -> FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ClumoColors.Panel, selectedLabelColor = ClumoColors.Text), selected = group == i, onClick = { group = i }, label = { Text(label) }) }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (failed) {
                        Text(stringResource(R.string.bindings_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { load() }, enabled = !busy) { Text(stringResource(R.string.bindings_reload)) }
                    }
                    val current = saved
                    if (current != null) {
                        val indices = when (group) {
                            0 -> listOf(0)
                            1 -> listOf(1)
                            else -> listOf(2, 3, 4, 5)
                        }
                        indices.forEach { i ->
                            OutlinedButton(border = BorderStroke(1.5.dp, ClumoColors.OutlineBorder), onClick = { editing = i }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(labels[i])
                                    Text((if (i == 5) rotation else actions)[current.value(i)])
                                }
                            }
                        }
                        StandHelp(title = stringResource(R.string.bindings_help_title), help = stringResource(R.string.bindings_help))
                        TextButton(onClick = { reset = true }, enabled = !busy) { Text(stringResource(R.string.bindings_reset)) }
                    } else if (!busy && !failed) {
                        TextButton(onClick = { load() }) { Text(stringResource(R.string.bindings_reload)) }
                    }
                }
            }
        }
    }
    editing?.let { index ->
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = ClumoColors.Background) {
            Text(labels[index], style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(22.dp))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                (if (index == 5) rotation else actions).forEachIndexed { value, label ->
                    TextButton(onClick = {
                        val next = saved?.changed(index, value)
                        editing = null
                        if (next != null) save(next)
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        }
    }
    if (reset) {
        AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.bindings_reset_confirm)) }, confirmButton = {
            TextButton(onClick = {
                reset = false
                save(ControlBindings.DEFAULT)
            }) { Text(stringResource(R.string.bindings_reset)) }
        }, dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.dialog_cancel)) } })
    }
}
