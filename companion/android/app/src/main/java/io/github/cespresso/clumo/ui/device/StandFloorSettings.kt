package io.github.cespresso.clumo.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.cespresso.clumo.data.ble.ControlBindings
import io.github.cespresso.clumo.ui.components.product.FloorSettingItem
import io.github.cespresso.clumo.ui.components.product.FloorSettingsSnapshot
import io.github.cespresso.clumo.ui.components.product.FloorSettingsState
import io.github.cespresso.clumo.ui.components.product.StandPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared settings state consumed by the Compose menu and legacy floor renderer. */
@Composable
internal fun StandFloorSettings(
    state: FloorSettingsState,
    part: StandPart?,
    ready: Boolean,
    brightness: Float,
    connectionLabel: String,
    onBrightness: (Float) -> Unit,
    onEditDisplay: () -> Unit,
    onClose: () -> Unit,
    onReconnect: () -> Unit,
    read: suspend () -> ControlBindings,
    save: suspend (ControlBindings) -> ControlBindings,
    onBusy: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val notifyBusy by rememberUpdatedState(onBusy)
    var bindings by remember(state) { mutableStateOf<ControlBindings?>(null) }
    var busy by remember(state) { mutableStateOf(false) }
    var failed by remember(state) { mutableStateOf(false) }
    var saveStatus by remember(state) { mutableStateOf<String?>(null) }
    var saveFailed by remember(state) { mutableStateOf(false) }
    val editIndex = state.destination.bindingIndex
    LaunchedEffect(editIndex) {
        saveStatus = null
        saveFailed = false
    }
    fun icon(index: Int, value: Int): String =
        if (index == 5) {
            if (value == 1) "volume" else "none"
        } else {
            listOf("none", "play", "previous", "next", "mute", "voice", "forward", "rewind").getOrElse(value) { "none" }
        }
    val labels = listOf("Left button", "Right button", "Single press", "Double press", "Triple press", "Rotate")
    val actions = listOf("None", "Play / Pause", "Previous track", "Next track", "Mute", "Voice assistant", "Fast forward (beta)", "Rewind (beta)")
    val rotations = listOf("Disabled", "Volume")
    fun run(change: ((ControlBindings) -> ControlBindings)? = null) {
        if (!ready || busy) return
        busy = true
        saveFailed = false
        saveStatus = if (change != null) "Saving…" else null
        notifyBusy(true)
        scope.launch {
            try {
                failed = false
                val fresh = read()
                bindings = fresh
                if (change != null) {
                    bindings = save(change(fresh))
                    saveStatus = "Saved"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (change == null) {
                    bindings = null
                    failed = true
                } else {
                    // Keep the last confirmed selection, never show the failed choice as saved.
                    saveStatus = "Could not save. Please try again."
                    saveFailed = true
                }
            } finally {
                busy = false
                notifyBusy(false)
            }
        }
    }
    // Fetch once on connection, before a part is selected. Part changes reuse this
    // device-scoped snapshot; writes still read fresh settings before applying changes.
    LaunchedEffect(state, ready) {
        bindings = null
        if (ready) run()
    }
    DisposableEffect(state) {
        onDispose {
            state.cancelClose()
            state.onAction = { _, _ -> }
            state.snapshot = FloorSettingsSnapshot()
            notifyBusy(false)
        }
    }
    val items = when {
        editIndex != null -> (if (editIndex == 5) rotations else actions).mapIndexed { value, title ->
            FloorSettingItem("action:$value", title, selected = bindings?.value(editIndex) == value, icon = icon(editIndex, value))
        }
        part == StandPart.Matrix -> listOf(FloorSettingItem("display", "Standby pattern", icon = "display"))
        part != null -> part.bindingIndices.map { index ->
            FloorSettingItem(
                "binding:$index",
                labels[index],
                bindings?.let { (if (index == 5) rotations else actions)[it.value(index)] } ?: "—",
                icon = bindings?.let { icon(index, it.value(index)) },
            )
        }
        else -> emptyList()
    }
    SideEffect {
        state.snapshot = FloorSettingsSnapshot(
            part = part,
            title = if (editIndex != null) {
                labels[editIndex]
            } else {
                when (part) {
                    StandPart.Knob -> "Knob"
                    StandPart.LeftButton -> "Left button"
                    StandPart.RightButton -> "Right button"
                    StandPart.Matrix -> "LED display"
                    null -> "Select a control"
                }
            },
            status = when {
                busy -> "Syncing…"
                !ready -> connectionLabel
                failed -> "Could not read settings · Retry"
                else -> "CLUMO / CONNECTED"
            },
            items = items,
            overviewAssignments = if (part == null && ready) {
                bindings?.let {
                    mapOf(
                        StandPart.LeftButton to actions[it.value(0)],
                        StandPart.RightButton to actions[it.value(1)],
                        StandPart.Knob to rotations[it.value(5)],
                    )
                }.orEmpty()
            } else {
                emptyMap()
            },
            brightness = brightness.toInt().toFloat().takeIf { part == StandPart.Matrix },
            enabled = ready && !busy && (part == StandPart.Matrix || bindings != null),
            busy = busy,
            choosing = editIndex != null,
            bindingIndex = editIndex,
            saveStatus = saveStatus,
            saveFailed = saveFailed,
        )
        state.onAction = action@{ id, value ->
            if (busy || state.closeToken != null || state.assignmentCloseToken != null) return@action
            if (id == "close") {
                onClose()
            } else if (id == "retry") {
                if (ready) run() else onReconnect()
            } else if (ready) {
                when {
                    id == "brightness" && part == StandPart.Matrix && value != null -> onBrightness(value.coerceIn(0f, 100f))
                    id == "display" && part == StandPart.Matrix -> onEditDisplay()
                    id.startsWith("binding:") && bindings != null -> state.selectBinding(id.substringAfter(':').toInt())
                    id.startsWith("action:") && editIndex != null && part != null && bindings != null -> {
                        val choice = id.substringAfter(':').toInt()
                        if (choice != bindings?.value(editIndex)) {
                            if (state.floorUi) {
                                state.navigateBack { run { changePartBinding(it, part, editIndex, choice) } }
                            } else {
                                run { changePartBinding(it, part, editIndex, choice) }
                            }
                        }
                    }
                    id == "reset" && part != null && bindings != null -> run { fresh ->
                        part.bindingIndices.fold(fresh) { result, index -> result.changed(index, ControlBindings.DEFAULT.value(index)) }
                    }
                }
            }
        }
    }
}
