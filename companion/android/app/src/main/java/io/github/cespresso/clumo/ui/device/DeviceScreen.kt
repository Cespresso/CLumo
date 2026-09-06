package io.github.cespresso.clumo.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.TimerStatus
import io.github.cespresso.clumo.service.DeviceHubService
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.components.CoralPillButton
import io.github.cespresso.clumo.ui.components.DeviceFace
import io.github.cespresso.clumo.ui.components.FaceBits
import io.github.cespresso.clumo.ui.components.NameInputDialog
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.SegmentedControl
import io.github.cespresso.clumo.ui.components.connectionFrameColor
import io.github.cespresso.clumo.ui.components.connectionLabel
import io.github.cespresso.clumo.ui.components.dashedBorder
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Live mirror of the device LED for the current mode.
 * Timer -> pixel countdown; Display -> selected pattern; Visualizer -> column bars.
 */
@Composable
fun liveMirrorBits(connection: DeviceConnection?, selectedPatternBits: String?): Long {
    if (connection == null) return FaceBits.EMPTY
    val state by connection.connectionState.collectAsState()
    val mode by connection.currentMode.collectAsState()
    val timer by connection.timerStatus.collectAsState()
    val columns by connection.audioVisualizer.columns.collectAsState()
    val vizActive by connection.audioVisualizer.isActive.collectAsState()
    if (state != ConnectionState.Ready) return FaceBits.EMPTY
    return when (mode) {
        BleUuids.MODE_TIMER -> timer?.let { FaceBits.fromTimer(it) } ?: FaceBits.EMPTY
        BleUuids.MODE_DISPLAY -> selectedPatternBits?.let { FaceBits.fromBitsString(it) }
            ?: FaceBits.EMPTY
        BleUuids.MODE_VISUALIZER -> if (vizActive) FaceBits.fromColumns(columns) else FaceBits.EMPTY
        else -> FaceBits.EMPTY
    }
}

@Composable
fun DeviceScreen(
    service: DeviceHubService,
    address: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (patternId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connections by service.registry.connections.collectAsState()
    val connection = connections[address]

    val state = connection?.connectionState?.collectAsState()?.value ?: ConnectionState.Disconnected
    val currentMode = connection?.currentMode?.collectAsState()?.value
    val timerStatus = connection?.timerStatus?.collectAsState()?.value
    val deviceId = connection?.deviceId?.collectAsState()?.value
    val scannedName = connection?.deviceName?.collectAsState()?.value

    val aliases by service.preferences.aliases.collectAsState(initial = emptyMap())
    val patterns by service.patterns.patterns.collectAsState(initial = emptyList())
    val selectedPatternId by service.patterns.selectedId.collectAsState(initial = null)
    val selectedPattern = patterns.firstOrNull { it.id == selectedPatternId }

    val knownDevice = service.repository.getByAddress(address)
    val stableId = deviceId ?: knownDevice?.id
    val displayName = stableId?.let { aliases[it] }
        ?: scannedName
        ?: knownDevice?.fallbackName
        ?: "CLumo"

    val ready = state == ConnectionState.Ready

    // Sync the segmented selector with the device (mode notifications included).
    var pendingMode by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentMode) { if (currentMode != null) pendingMode = null }
    val effectiveMode = pendingMode ?: currentMode ?: BleUuids.MODE_TIMER

    // Re-read MODE when entering the screen so the selector starts in sync.
    LaunchedEffect(connection, ready) {
        if (ready) connection?.readMode()
    }

    // Brightness: UI 0..100, quantized to 0..15, written only when the
    // quantized value changes (throttled ~100ms).
    val deviceBrightness = connection?.brightness?.collectAsState()?.value ?: 15
    var brightnessUi by remember { mutableFloatStateOf(deviceBrightness / 15f * 100f) }
    var brightnessDragging by remember { mutableStateOf(false) }
    LaunchedEffect(deviceBrightness) {
        if (!brightnessDragging) brightnessUi = deviceBrightness / 15f * 100f
    }
    LaunchedEffect(connection) {
        if (connection == null) return@LaunchedEffect
        snapshotFlow { (brightnessUi / 100f * 15f).roundToInt().coerceIn(0, 15) }
            .distinctUntilChanged()
            .collectLatest { quantized ->
                delay(100)
                if (quantized != connection.brightness.value) {
                    connection.writeBrightness(quantized)
                }
            }
    }
    val litAlpha = 0.4f + (brightnessUi / 100f) * 0.6f

    // While in Display mode, keep the device showing the selected pattern.
    LaunchedEffect(ready, effectiveMode, selectedPattern?.bits, connection) {
        if (ready && effectiveMode == BleUuids.MODE_DISPLAY && selectedPattern != null) {
            connection?.writeDisplay(selectedPattern.toRowBytes())
        }
    }

    val mirrorBits = liveMirrorBits(connection, selectedPattern?.bits)
    val frameColor = connectionFrameColor(state)
    val (stateLabel, stateLabelColor) = connectionLabel(state)

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "‹",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    text = displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { menuOpen = !menuOpen }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.5.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(ClumoColors.Muted),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Connection-lost banner
                if (state == ConnectionState.Error) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ClumoColors.ErrorBg)
                            .border(1.5.dp, ClumoColors.ErrorBorder, RoundedCornerShape(18.dp))
                            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ClumoColors.Coral),
                        )
                        Text(
                            text = stringResource(R.string.device_error_banner),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.ErrorText,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClumoColors.Coral)
                                .clickable {
                                    service.registry.connect(address, scannedName)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.device_error_retry),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = RoundedFontFamily,
                                color = ClumoColors.White,
                            )
                        }
                    }
                }

                // Device face: the live LED mirror
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeviceFace(
                        bits = mirrorBits,
                        frameColor = frameColor,
                        size = 188.dp,
                        litAlpha = litAlpha,
                        shadowElevation = 14.dp,
                    )
                    Text(
                        text = stateLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RoundedFontFamily,
                        color = stateLabelColor,
                    )
                }

                // Controls: dimmed and non-interactive while disconnected.
                Box {
                    Column(modifier = Modifier.alpha(if (ready) 1f else 0.45f)) {
                        // Brightness
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.device_brightness),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = RoundedFontFamily,
                                color = ClumoColors.Muted,
                            )
                            ClumoSlider(
                                value = brightnessUi,
                                onValueChange = {
                                    brightnessDragging = true
                                    brightnessUi = it
                                },
                                modifier = Modifier.weight(1f),
                                enabled = ready,
                            )
                        }
                        LaunchedEffect(brightnessUi) {
                            // Release the "dragging" hold shortly after the last move.
                            delay(300)
                            brightnessDragging = false
                        }

                        // Mode selector
                        SegmentedControl(
                            items = listOf(
                                stringResource(R.string.seg_timer),
                                stringResource(R.string.seg_patterns),
                                stringResource(R.string.seg_viz),
                            ),
                            selectedIndex = effectiveMode.coerceIn(0, 2),
                            onSelect = { index ->
                                if (index != effectiveMode) {
                                    pendingMode = index
                                    connection?.writeMode(index)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                        )

                        // Function area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 40.dp),
                        ) {
                            when (effectiveMode) {
                                BleUuids.MODE_DISPLAY -> PatternsSection(
                                    patterns = patterns,
                                    selectedId = selectedPatternId,
                                    onSelect = { pattern ->
                                        scope.launch { service.patterns.select(pattern.id) }
                                    },
                                    onAddNew = { onOpenEditor(null) },
                                    onEditSelected = {
                                        selectedPatternId?.let { onOpenEditor(it) }
                                    },
                                )

                                BleUuids.MODE_VISUALIZER -> VisualizerSection(
                                    connection = connection,
                                )

                                else -> TimerSection(
                                    connection = connection,
                                    status = timerStatus ?: TimerStatus.DEFAULT,
                                )
                            }
                        }
                    }
                    if (!ready) {
                        // Swallow all input over the controls while disconnected.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                        )
                    }
                }
            }
        }

        // 3-dot menu
        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { menuOpen = false },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = 52.dp)
                    .widthIn(min = 176.dp)
                    .shadow(14.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClumoColors.White)
                    .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(20.dp))
                    .padding(8.dp),
            ) {
                MenuItem(stringResource(R.string.device_menu_rename), ClumoColors.Text) {
                    menuOpen = false
                    renameOpen = true
                }
                MenuItem(stringResource(R.string.device_menu_settings), ClumoColors.Text) {
                    menuOpen = false
                    onOpenSettings()
                }
                MenuItem(stringResource(R.string.device_menu_disconnect), ClumoColors.Coral) {
                    menuOpen = false
                    service.registry.disconnect(address)
                    onBack()
                }
            }
        }
    }

    if (renameOpen) {
        NameInputDialog(
            title = stringResource(R.string.rename_title),
            initialValue = displayName,
            placeholder = null,
            onConfirm = { name ->
                renameOpen = false
                val id = stableId ?: return@NameInputDialog
                scope.launch { service.preferences.setAlias(id, name) }
            },
            onDismiss = { renameOpen = false },
        )
    }
}

@Composable
private fun MenuItem(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = color,
        )
    }
}

// ---------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------

@Composable
private fun TimerSection(
    connection: DeviceConnection?,
    status: TimerStatus,
) {
    var workMin by remember { mutableIntStateOf(status.workMin.coerceIn(1, 99)) }
    var breakMin by remember { mutableIntStateOf(status.breakMin.coerceIn(1, 99)) }
    LaunchedEffect(status.workMin, status.breakMin) {
        workMin = status.workMin.coerceIn(1, 99)
        breakMin = status.breakMin.coerceIn(1, 99)
    }

    fun pushDurations() {
        connection?.timerSetDurations(workMin, breakMin)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Phase chip
        val work = status.isWorkPhase
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (work) ClumoColors.Sage else ClumoColors.BreakChipBg)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stringResource(
                    if (work) R.string.timer_phase_work else R.string.timer_phase_break
                ),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = if (work) ClumoColors.White else ClumoColors.BreakChipFg,
            )
        }

        // Remaining time
        Text(
            text = "%02d:%02d".format(status.remainingSec / 60, status.remainingSec % 60),
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
        )

        // Duration steppers (visible while idle)
        if (status.isIdle) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DurationStepperRow(
                    label = stringResource(R.string.timer_work_label),
                    value = workMin,
                    onChange = { workMin = it; pushDurations() },
                )
                DurationStepperRow(
                    label = stringResource(R.string.timer_break_label),
                    value = breakMin,
                    onChange = { breakMin = it; pushDurations() },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoralPillButton(
                text = stringResource(
                    if (status.isRunning) R.string.timer_pause else R.string.timer_start
                ),
                onClick = {
                    if (status.isRunning) connection?.timerPause() else connection?.timerStart()
                },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1.4f),
            )
            OutlinePillButton(
                text = stringResource(R.string.timer_reset),
                onClick = { connection?.timerReset() },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DurationStepperRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier.weight(1f),
        )
        StepperButton(text = "−", enabled = value > 1) { onChange((value - 1).coerceIn(1, 99)) }
        Text(
            text = "$value",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepperButton(text = "＋", enabled = value < 99) { onChange((value + 1).coerceIn(1, 99)) }
        Text(
            text = stringResource(R.string.timer_minutes_unit),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.OutlineBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
        )
    }
}

// ---------------------------------------------------------------------------
// Patterns
// ---------------------------------------------------------------------------

@Composable
private fun PatternsSection(
    patterns: List<Pattern>,
    selectedId: String?,
    onSelect: (Pattern) -> Unit,
    onAddNew: () -> Unit,
    onEditSelected: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 3-column grid of saved patterns plus the "add new" tile.
        val tiles: List<Pattern?> = patterns + listOf<Pattern?>(null)
        tiles.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowItems.forEach { pattern ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (pattern == null) {
                            AddPatternTile(onClick = onAddNew)
                        } else {
                            PatternTile(
                                pattern = pattern,
                                selected = pattern.id == selectedId,
                                onClick = { onSelect(pattern) },
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        OutlinePillButton(
            text = stringResource(R.string.patterns_edit_selected),
            onClick = onEditSelected,
            fontSize = 14.sp,
            verticalPadding = 13.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            enabled = selectedId != null,
        )
    }
}

@Composable
private fun PatternTile(
    pattern: Pattern,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box {
            DeviceFace(
                bits = FaceBits.fromBitsString(pattern.bits),
                frameColor = if (selected) ClumoColors.Sage else ClumoColors.Gray,
                size = 96.dp,
                frameCorner = 22.dp,
                framePadding = 12.dp,
                innerCorner = 11.dp,
                gridPadding = 7.dp,
                glow = false,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(24.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ClumoColors.Sage),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.White,
                    )
                }
            }
        }
        Text(
            text = pattern.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = if (selected) ClumoColors.Text else ClumoColors.Muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddPatternTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .dashedBorder(ClumoColors.Chevron, 2.5.dp, 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "＋",
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.MutedLight,
            )
        }
        Text(
            text = stringResource(R.string.patterns_add_new),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Visualizer
// ---------------------------------------------------------------------------

@Composable
private fun VisualizerSection(connection: DeviceConnection?) {
    val context = LocalContext.current
    val vizActive = connection?.audioVisualizer?.isActive?.collectAsState()?.value ?: false
    var sensitivity by remember {
        mutableFloatStateOf((connection?.audioVisualizer?.sensitivity ?: 0.6f) * 100f)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            connection?.startAudioVisualizer()
        }
    }

    fun toggle() {
        if (connection == null) return
        if (vizActive) {
            connection.stopAudioVisualizer()
            return
        }
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            audioPermissionLauncher.launch(needed.toTypedArray())
        } else {
            connection.startAudioVisualizer()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (vizActive) {
            OutlinePillButton(
                text = stringResource(R.string.viz_stop),
                onClick = { toggle() },
                fontSize = 16.sp,
                verticalPadding = 17.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CoralPillButton(
                text = stringResource(R.string.viz_start),
                onClick = { toggle() },
                fontSize = 16.sp,
                verticalPadding = 17.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viz_sensitivity),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
            )
            ClumoSlider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    connection?.audioVisualizer?.sensitivity = it / 100f
                },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = stringResource(R.string.viz_caption),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
