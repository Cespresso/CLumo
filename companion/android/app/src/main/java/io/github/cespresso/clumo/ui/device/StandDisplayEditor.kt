package io.github.cespresso.clumo.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.edit
import io.github.cespresso.clumo.data.ble.StandDisplaySettings
import io.github.cespresso.clumo.ui.components.ScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

private enum class PatternPage(val title: String) {
    Picker("Standby pattern"),
    Create("Create pattern"),
    AI("Create with AI"),
    Paste("Paste JSON"),
    Edit("Edit pattern"),
}

@Composable
internal fun StandDisplayEditor(
    onPreview: (Long?) -> Unit,
    readSettings: suspend () -> StandDisplaySettings,
    saveSettings: suspend (StandDisplaySettings) -> StandDisplaySettings,
    ready: Boolean,
    spatial: Boolean,
    accent: Color,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("standby_patterns_v1", Context.MODE_PRIVATE) }
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val library = remember {
        mutableStateMapOf<String, StandbyPattern>().apply {
            putAll(readStandbyLibrary(prefs))
        }
    }
    var page by rememberSaveable { mutableStateOf(PatternPage.Picker) }
    var pasteParent by rememberSaveable { mutableStateOf(PatternPage.Create) }
    var editParent by rememberSaveable { mutableStateOf(PatternPage.Create) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftJson by rememberSaveable { mutableStateOf(StandbyPattern("Untitled").json().toString()) }
    var pasteText by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var changed by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var preferredChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<StandbyPattern?>(null) }
    val scope = rememberCoroutineScope()
    fun back() {
        if (busy) return
        message = ""
        when (page) {
            PatternPage.Picker -> onClose()
            PatternPage.Create -> page = PatternPage.Picker
            PatternPage.AI -> page = PatternPage.Create
            PatternPage.Paste -> page = pasteParent
            PatternPage.Edit -> if (changed) discard = true else page = editParent
        }
    }
    fun edit(pattern: StandbyPattern, id: String?, parent: PatternPage) {
        draftJson = pattern.json().toString()
        editingId = id
        editParent = parent
        changed = false
        message = ""
        page = PatternPage.Edit
    }
    fun paste(parent: PatternPage) {
        pasteParent = parent
        message = ""
        pasteText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty().take(16385)
        page = PatternPage.Paste
    }
    val readCurrentSettings by rememberUpdatedState(readSettings)
    LaunchedEffect(ready, retry) {
        current = null
        if (ready) {
            try {
                current = StandbyPattern.fromDevice(readCurrentSettings())
                message = ""
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                message = "The stand didn’t respond. Try again."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Couldn’t read the stand."
            }
        }
    }
    val previewCallback by rememberUpdatedState(onPreview)
    DisposableEffect(Unit) { onDispose { previewCallback(null) } }
    LaunchedEffect(page, current, ready, spatial) {
        val pattern = current
        if (!spatial || !ready || page != PatternPage.Picker || pattern == null) {
            previewCallback(null)
            return@LaunchedEffect
        }
        val settings = pattern.settings(0)
        val mode = settings.encode()[1].toInt()
        val period = settings.framePeriod(mode)
        var step = 0
        do {
            previewCallback(settings.frameBits(mode, step++))
            if (period > 0) delay(period)
        } while (period > 0)
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val scheme = MaterialTheme.colorScheme
        MaterialTheme(colorScheme = scheme.copy(primary = accent)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val top = if (spatial && page == PatternPage.Picker) maxHeight * .52f else 0.dp
                Surface(Modifier.padding(top = top).fillMaxSize(), color = scheme.background) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        ScreenHeader(title = page.title, onBack = ::back)
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollStateForPage(page)).padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (message.isNotEmpty()) Text(message)
                            when (page) {
                                PatternPage.Picker -> {
                                    val choices = buildList<Pair<String?, StandbyPattern>> {
                                        addAll(StandbyPattern.presets.map { null to it })
                                        addAll(library.entries.sortedBy { it.value.name }.map { it.key to it.value })
                                        add(null to StandbyPattern.off)
                                        current?.let { live -> if (none { it.second.matches(live) }) add(0, null to live) }
                                    }
                                    val matching = choices.indices.filter { current?.matches(choices[it].second) == true }
                                    val selected = matching.firstOrNull {
                                        val (id, pattern) = choices[it]
                                        (id ?: pattern.name) == preferredChoice
                                    } ?: matching.firstOrNull()
                                    if (!ready) {
                                        Text("Connect your stand to apply a pattern.")
                                    } else if (current == null) {
                                        TextButton(onClick = { retry++ }) { Text("Read from stand") }
                                    }
                                    Column(Modifier.selectableGroup()) {
                                        choices.forEachIndexed { index, (id, pattern) ->
                                            val enabled = ready && current != null && !busy
                                            ListItem(
                                                headlineContent = { Text(pattern.name) },
                                                leadingContent = { DotPreview(pattern.frames.first(), accent, Modifier.size(36.dp)) },
                                                trailingContent = { RadioButton(selected == index, null, enabled = enabled) },
                                                modifier = Modifier.selectable(selected == index, enabled, Role.RadioButton) {
                                                    if (selected != index && !busy) {
                                                        busy = true
                                                        scope.launch {
                                                            try {
                                                                val brightness = readSettings().encode()[0]
                                                                current = StandbyPattern.fromDevice(saveSettings(pattern.settings(brightness)))
                                                                preferredChoice = id ?: pattern.name
                                                                message = ""
                                                            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                                                                message = "The stand didn’t confirm the change."
                                                            } catch (e: CancellationException) {
                                                                throw e
                                                            } catch (_: Exception) {
                                                                message = "Couldn’t apply the pattern. Try again."
                                                            } finally {
                                                                busy = false
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                            if ((index == selected && pattern.mode != "off") || id != null) {
                                                Row {
                                                    TextButton(onClick = { edit(pattern, id, PatternPage.Picker) }, enabled = !busy) {
                                                        Text(if (id != null) "Edit pattern" else "Edit a copy")
                                                    }
                                                    if (id != null) {
                                                        TextButton(onClick = { deleteId = id }, enabled = !busy) {
                                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Button(onClick = {
                                        page = PatternPage.Create
                                        message = ""
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+  Create pattern") }
                                }
                                PatternPage.Create -> {
                                    CreationRow("Draw pattern", "Draw on the 8 × 8 grid.") { edit(StandbyPattern("Untitled"), null, page) }
                                    CreationRow("Create with AI", "Design and preview in your preferred AI app.") { page = PatternPage.AI }
                                    CreationRow("Paste JSON", "Import a pattern you already have.") { paste(page) }
                                }
                                PatternPage.AI -> {
                                    Text("Copy the prompt", style = MaterialTheme.typography.titleLarge)
                                    Text("Paste it into your preferred AI app and describe your animation.")
                                    Button(onClick = {
                                        val prompt = context.assets.open("standby-designer.txt").bufferedReader().use { it.readText() }
                                        clipboard.setPrimaryClip(ClipData.newPlainText("CLumo standby designer", prompt))
                                        message = "Prompt copied"
                                    }) { Text("Copy prompt") }
                                    HorizontalDivider()
                                    Text("Bring your pattern back", style = MaterialTheme.typography.titleLarge)
                                    Text("Copy the finished JSON, then paste it here to preview and edit.")
                                    OutlinedButton(onClick = { paste(page) }) { Text("Paste JSON") }
                                }
                                PatternPage.Paste -> {
                                    OutlinedTextField(
                                        pasteText,
                                        {
                                            pasteText = it.take(16385)
                                            message = ""
                                        },
                                        label = { Text("Pattern JSON") },
                                        minLines = 4,
                                        maxLines = 8,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(enabled = pasteText.isNotBlank(), onClick = {
                                        try {
                                            edit(StandbyPattern.parse(pasteText), null, page)
                                        } catch (_: Exception) {
                                            message = "Invalid pattern. Use clumo.standby v1 with 1–8 frames of eight binary rows."
                                        }
                                    }) { Text("Preview and edit") }
                                }
                                PatternPage.Edit -> {
                                    val draft = remember(draftJson) { StandbyPattern.parse(draftJson) }
                                    PatternDrawing(
                                        draft,
                                        accent,
                                        onChange = {
                                            draftJson = it.json().toString()
                                            changed = true
                                        },
                                        onSave = { pattern ->
                                            val id = editingId ?: UUID.randomUUID().toString()
                                            prefs.edit { putString(id, pattern.json().toString()) }
                                            library[id] = pattern
                                            changed = false
                                            page = PatternPage.Picker
                                            message = "Saved to your library"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        deleteId?.let { id ->
            library[id]?.let { pattern ->
                AlertDialog(
                    onDismissRequest = { deleteId = null },
                    title = { Text("Delete “${pattern.name}” from your library?") },
                    confirmButton = {
                        TextButton(onClick = {
                            prefs.edit { remove(id) }
                            library.remove(id)
                            if (preferredChoice == id) preferredChoice = null
                            deleteId = null
                            message = "Deleted from your library"
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
                )
            }
        }
        if (discard) {
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text("Discard changes?") },
                confirmButton = {
                    TextButton(onClick = {
                        discard = false
                        changed = false
                        page = editParent
                    }) { Text("Discard") }
                },
                dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
            )
        }
    }
}

@Composable
private fun rememberScrollStateForPage(page: PatternPage) = key(page) { rememberScrollState() }

@Composable
private fun CreationRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Text("›") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DotPreview(rows: List<Int>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / 8
        for (y in 0..7) {
            for (x in 0..7) {
                drawCircle(
                    if (rows[y] and (128 shr x) != 0) accent else Color.Gray.copy(alpha = .18f),
                    cell * .36f,
                    Offset((x + .5f) * cell, (y + .5f) * cell),
                )
            }
        }
    }
}

@Composable
private fun PatternDrawing(draft: StandbyPattern, accent: Color, onChange: (StandbyPattern) -> Unit, onSave: (StandbyPattern) -> Unit) {
    var name by rememberSaveable { mutableStateOf(draft.name) }
    var frame by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var scrollStep by remember { mutableIntStateOf(0) }
    val index = frame.coerceIn(draft.frames.indices)
    LaunchedEffect(playing, draft.duration, draft.mode) {
        while (playing) {
            delay(draft.duration.toLong())
            if (draft.mode == "scroll") {
                scrollStep = (scrollStep + 1) % 8
            } else {
                frame = (frame + 1) % draft.frames.size
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            name,
            {
                name = it
                onChange(draft.copy(name = it.ifBlank { "Untitled" }))
            },
            label = { Text("Pattern name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val rows = draft.frames[index]
        Column(Modifier.fillMaxWidth(.85f).widthIn(max = 360.dp).aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)) {
            for (y in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (x in 0..7) {
                        val bit = 128 shr (if (playing && draft.mode == "scroll") (x + scrollStep) % 8 else x)
                        val on = rows[y] and bit != 0
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable(enabled = !playing) {
                                val frames = draft.frames.toMutableList()
                                frames[index] = rows.toMutableList().apply { this[y] = this[y] xor bit }
                                onChange(draft.copy(frames = frames, mode = if (draft.mode == "off") "frames" else draft.mode))
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Spacer(Modifier.fillMaxSize(.72f).aspectRatio(1f).background(if (on) accent else Color.Gray.copy(alpha = .22f), androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            draft.frames.forEachIndexed { i, dots ->
                OutlinedButton(
                    onClick = {
                        playing = false
                        frame = i
                    },
                    contentPadding = PaddingValues(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (index == i) accent.copy(alpha = .2f) else Color.Transparent),
                ) {
                    DotPreview(dots, accent, Modifier.size(32.dp))
                }
            }
            if (draft.frames.size < 8 && draft.mode != "scroll") {
                TextButton(onClick = {
                    playing = false
                    frame = draft.frames.size
                    onChange(draft.copy(mode = "frames", frames = draft.frames + listOf(rows)))
                }) { Text("+ Frame") }
            }
        }
        if (draft.frames.size > 1 || draft.mode == "scroll") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    playing = !playing
                    scrollStep = 0
                }) { Text(if (playing) "Pause" else "Play") }
                if (draft.frames.size > 1) {
                    TextButton(onClick = {
                        playing = false
                        frame = (index - 1).coerceAtLeast(0)
                        onChange(draft.copy(frames = draft.frames.filterIndexed { i, _ -> i != index }))
                    }) { Text("Remove frame") }
                }
            }
            Text("Frame duration · ${draft.duration} ms")
            if (draft.frames.size > 1) {
                Slider(value = draft.duration.toFloat(), onValueChange = {
                    onChange(draft.copy(duration = ((it / 50f).roundToInt() * 50).coerceIn(100, 2000)))
                }, valueRange = 100f..2000f, steps = 37)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scroll left", modifier = Modifier.weight(1f))
                Switch(false, { onChange(draft.copy(mode = "scroll", duration = 600)) })
            }
        }
        if (draft.mode == "scroll") {
            TextButton(onClick = {
                playing = false
                onChange(draft.copy(mode = "frames"))
            }) { Text("Stop scrolling left") }
        }
        Button(onClick = { onSave(draft.copy(name = name.trim())) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save pattern") }
    }
}
