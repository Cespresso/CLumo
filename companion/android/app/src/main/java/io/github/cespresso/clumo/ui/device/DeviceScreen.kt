package io.github.cespresso.clumo.ui.device

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.service.DeviceHubService
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.appearance.resolveAppearance
import io.github.cespresso.clumo.ui.components.ClumoToggleSwitch
import io.github.cespresso.clumo.ui.components.ClumoActionDialog
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.DeviceFace
import io.github.cespresso.clumo.ui.components.FaceBits
import io.github.cespresso.clumo.ui.components.ModeHelpDialog
import io.github.cespresso.clumo.ui.components.ModeHelpHeader
import io.github.cespresso.clumo.ui.components.NameInputDialog
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.SegmentedControl
import io.github.cespresso.clumo.ui.components.ContentTone
import io.github.cespresso.clumo.ui.components.contentToneFor
import io.github.cespresso.clumo.ui.components.dashedBorder
import io.github.cespresso.clumo.ui.components.toComposeColor
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Live mirror of the device LED for the current mode.
 * Pomodoro/Timer -> pixel countdown; Display -> selected pattern; Visualizer -> column bars.
 */
@Composable
fun liveMirrorBits(
    connection: DeviceConnection?,
    selectedPatternBits: String?,
): Long {
    if (connection == null) return FaceBits.EMPTY
    val state by connection.connectionState.collectAsState()
    val mode by connection.currentMode.collectAsState()
    val pomodoro by connection.pomodoroStatus.collectAsState()
    val timer by connection.timerStatus.collectAsState()
    val timerBlinkOn = completionBlink(
        mode == BleUuids.MODE_TIMER && timer?.isCompleted == true
    )
    val columns by connection.audioVisualizer.columns.collectAsState()
    val vizActive by connection.audioVisualizer.isActive.collectAsState()
    if (state != ConnectionState.Ready) return FaceBits.EMPTY
    return when (mode) {
        BleUuids.MODE_POMODORO -> pomodoro?.let { FaceBits.fromPomodoro(it) } ?: FaceBits.EMPTY
        BleUuids.MODE_TIMER -> timer?.let {
            if (it.isCompleted && !timerBlinkOn) FaceBits.EMPTY
            else if (it.isCompleted) -1L
            else FaceBits.fromCountdownTimer(it)
        } ?: FaceBits.EMPTY
        BleUuids.MODE_DISPLAY -> selectedPatternBits?.let { FaceBits.fromBitsString(it) }
            ?: FaceBits.EMPTY
        BleUuids.MODE_VISUALIZER -> if (vizActive) FaceBits.fromColumns(columns) else FaceBits.EMPTY
        else -> FaceBits.EMPTY
    }
}

@Composable
private fun completionBlink(active: Boolean): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        visible = true
        while (active) {
            delay(400)
            visible = !visible
        }
    }
    return visible
}

@Composable
fun DeviceScreen(
    service: DeviceHubService,
    address: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: (deviceId: String) -> Unit,
    onOpenEditor: (patternId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connections by service.registry.connections.collectAsState()
    val connection = connections[address]
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        connection?.connect()
    }

    val state = connection?.connectionState?.collectAsState()?.value ?: ConnectionState.Disconnected
    val failure = connection?.connectionFailure?.collectAsState()?.value
    val reconnectAttempt = connection?.reconnectAttempt?.collectAsState()?.value ?: 0
    val currentMode = connection?.currentMode?.collectAsState()?.value
    val pomodoroStatus = connection?.pomodoroStatus?.collectAsState()?.value
    val timerStatus = connection?.timerStatus?.collectAsState()?.value
    val deviceId = connection?.deviceId?.collectAsState()?.value
    val scannedName = connection?.deviceName?.collectAsState()?.value

    val aliases by service.preferences.aliases.collectAsState(initial = emptyMap())
    val appearances by service.preferences.deviceAppearances.collectAsState(initial = emptyMap())
    val primaryDeviceId by service.preferences.primaryDeviceId.collectAsState(initial = null)
    val visualizerSensitivity by service.preferences.visualizerSensitivity.collectAsState(initial = 0.6f)
    val automaticLowVolumeBoost by service.preferences.automaticLowVolumeBoost.collectAsState(initial = false)
    val patterns by service.patterns.patterns.collectAsState(initial = emptyList())
    val selectedPatternId by service.patterns.selectedId.collectAsState(initial = null)
    val selectedPattern = patterns.firstOrNull { it.id == selectedPatternId }

    val knownDevice = service.repository.getByAddress(address)
    val stableId = deviceId ?: knownDevice?.id
    val appearance = resolveAppearance(stableId, appearances)
    val displayName = stableId?.let { aliases[it] }
        ?: scannedName
        ?: knownDevice?.fallbackName
        ?: "CLumo"

    val ready = state == ConnectionState.Ready

    // Sync the segmented selector with the device (mode notifications included).
    var pendingMode by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentMode) { if (currentMode != null) pendingMode = null }
    val effectiveMode = pendingMode ?: currentMode ?: BleUuids.MODE_POMODORO

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

    val timerBlinkOn = completionBlink(
        effectiveMode == BleUuids.MODE_TIMER && timerStatus?.isCompleted == true
    )
    val mirrorBits = liveMirrorBits(connection, selectedPattern?.bits)
    val stateLabel = when (state) {
        ConnectionState.Connecting -> stringResource(R.string.state_connecting)
        ConnectionState.Reconnecting -> stringResource(R.string.state_reconnecting, reconnectAttempt)
        ConnectionState.Bonding -> stringResource(R.string.state_pairing)
        ConnectionState.Connected -> stringResource(R.string.state_discovering)
        ConnectionState.Synchronizing -> stringResource(R.string.state_synchronizing)
        ConnectionState.Ready -> stringResource(R.string.state_connected)
        ConnectionState.Error -> stringResource(R.string.state_error)
        ConnectionState.Disconnected -> stringResource(R.string.state_disconnected)
    }
    val stateLabelColor = when (state) {
        ConnectionState.Ready -> LocalClumoAccents.current.accent
        ConnectionState.Error -> ClumoColors.Coral
        else -> ClumoColors.Muted
    }
    val failureMessage = when (failure) {
        ConnectionFailure.BluetoothUnavailable -> stringResource(R.string.connection_error_unavailable)
        ConnectionFailure.BluetoothDisabled -> stringResource(R.string.connection_error_bluetooth_off)
        ConnectionFailure.PermissionDenied -> stringResource(R.string.connection_error_permission)
        ConnectionFailure.ConnectionTimedOut -> stringResource(R.string.connection_error_timeout)
        ConnectionFailure.PairingFailed -> stringResource(R.string.connection_error_pairing)
        ConnectionFailure.ServiceDiscoveryFailed -> stringResource(R.string.connection_error_services)
        ConnectionFailure.IncompatibleDevice -> stringResource(R.string.connection_error_incompatible)
        ConnectionFailure.SynchronizationFailed -> stringResource(R.string.connection_error_sync)
        ConnectionFailure.ConnectionLost -> stringResource(R.string.device_error_banner)
        null -> stringResource(R.string.device_error_banner)
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var modeHelpOpen by remember { mutableStateOf(false) }
    var dismissedDialogFailure by remember { mutableStateOf<ConnectionFailure?>(null) }
    LaunchedEffect(failure) {
        if (failure == null) dismissedDialogFailure = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
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
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .padding(bottom = 40.dp),
            ) {
                if (state == ConnectionState.Bonding) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ClumoColors.White)
                            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "i",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = RoundedFontFamily,
                            color = LocalClumoAccents.current.accent,
                        )
                        Text(
                            text = stringResource(R.string.connection_pairing_hint),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.Text,
                        )
                    }
                }

                // Automatic reconnect progress and terminal connection failures.
                if (state == ConnectionState.Error || state == ConnectionState.Reconnecting) {
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
                            text = if (state == ConnectionState.Reconnecting) {
                                stringResource(R.string.connection_reconnecting_banner, reconnectAttempt)
                            } else {
                                failureMessage
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.ErrorText,
                            modifier = Modifier.weight(1f),
                        )
                        if (state == ConnectionState.Error) {
                            val accents = LocalClumoAccents.current
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(accents.cta)
                                    .clickable {
                                        when (failure) {
                                            ConnectionFailure.PermissionDenied -> settingsLauncher.launch(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"),
                                                )
                                            )
                                            ConnectionFailure.BluetoothDisabled -> settingsLauncher.launch(
                                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            )
                                            else -> service.registry.connect(address, scannedName)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = when (failure) {
                                        ConnectionFailure.PermissionDenied,
                                        ConnectionFailure.BluetoothDisabled -> stringResource(R.string.action_open_settings)
                                        else -> stringResource(R.string.device_error_retry)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RoundedFontFamily,
                                    color = accents.onCta,
                                )
                            }
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
                    ClumoDevice(
                        bits = mirrorBits,
                        size = 188.dp,
                        appearance = appearance,
                        connectionState = state,
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

                Column {
                    // Device controls: dimmed and non-interactive while disconnected.
                    Box {
                        Column(
                            modifier = Modifier
                                .alpha(if (ready) 1f else 0.45f)
                                .then(
                                    if (ready) Modifier else Modifier.clearAndSetSemantics {
                                        disabled()
                                    }
                                ),
                        ) {
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
                                    stringResource(R.string.seg_pomodoro),
                                    stringResource(R.string.seg_timer),
                                    stringResource(R.string.seg_patterns),
                                    stringResource(R.string.seg_viz),
                                ),
                                selectedIndex = effectiveMode.coerceIn(0, 3),
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
                        }
                        if (!ready) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    )
                                    .clearAndSetSemantics {},
                            )
                        }
                    }

                    ModeHelpHeader(
                        mode = effectiveMode,
                        onHelpClick = { modeHelpOpen = true },
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                    )

                    // Mode-specific controls remain unavailable while disconnected.
                    Box {
                        // Function area
                        Box(
                            modifier = Modifier
                                .alpha(if (ready) 1f else 0.45f)
                                .then(
                                    if (ready) Modifier else Modifier.clearAndSetSemantics {
                                        disabled()
                                    }
                                )
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 22.dp, top = 2.dp),
                        ) {
                            when (effectiveMode) {
                                BleUuids.MODE_DISPLAY -> PatternsSection(
                                    patterns = patterns,
                                    selectedId = selectedPatternId,
                                    appearance = appearance,
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
                                    visualizerSensitivity = visualizerSensitivity,
                                    automaticLowVolumeBoost = automaticLowVolumeBoost,
                                    onVisualizerSensitivityChange = { sensitivity ->
                                        connection?.audioVisualizer?.sensitivity = sensitivity
                                    },
                                    onVisualizerSensitivityChangeFinished = { sensitivity ->
                                        scope.launch {
                                            service.preferences.setVisualizerSensitivity(sensitivity)
                                        }
                                    },
                                    onAutomaticLowVolumeBoostChange = { enabled ->
                                        connection?.audioVisualizer?.automaticLowVolumeBoost = enabled
                                        scope.launch {
                                            service.preferences.setAutomaticLowVolumeBoost(enabled)
                                        }
                                    },
                                )

                                BleUuids.MODE_POMODORO -> PomodoroSection(
                                    connection = connection,
                                    status = pomodoroStatus ?: PomodoroStatus.DEFAULT,
                                )

                                BleUuids.MODE_TIMER -> CountdownTimerSection(
                                    connection = connection,
                                    status = timerStatus ?: CountdownTimerStatus.DEFAULT,
                                    completionBlinkOn = timerBlinkOn,
                                )

                                else -> Unit
                            }
                        }
                        if (!ready) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    )
                                    .clearAndSetSemantics {},
                            )
                        }
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
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End,
                        ),
                    )
                    .offset(x = (-16).dp, y = 52.dp)
                    .widthIn(min = 176.dp)
                    .width(IntrinsicSize.Max)
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
                MenuItem(
                    label = stringResource(R.string.device_menu_appearance),
                    color = ClumoColors.Text,
                    enabled = stableId != null,
                ) {
                    menuOpen = false
                    stableId?.let(onOpenAppearance)
                }
                val isPrimary = stableId != null && stableId == primaryDeviceId
                MenuItem(
                    label = stringResource(
                        if (isPrimary) R.string.device_menu_unset_primary else R.string.device_menu_set_primary
                    ),
                    color = ClumoColors.Text,
                    enabled = stableId != null,
                ) {
                    menuOpen = false
                    stableId?.let { id ->
                        scope.launch {
                            service.preferences.setPrimaryDeviceId(if (isPrimary) null else id)
                        }
                    }
                }
                MenuItem(stringResource(R.string.device_menu_settings), ClumoColors.Text) {
                    menuOpen = false
                    onOpenSettings()
                }
                MenuItem(stringResource(R.string.device_menu_refresh_gatt), ClumoColors.Text) {
                    menuOpen = false
                    service.registry.get(address)?.reconnectWithCacheRefresh()
                        ?: service.registry.connect(address, scannedName)
                }
                MenuItem(stringResource(R.string.device_menu_disconnect), ClumoColors.Coral) {
                    menuOpen = false
                    service.registry.disconnect(address)
                    onBack()
                }
            }
        }
    }

    if (modeHelpOpen) {
        ModeHelpDialog(
            mode = effectiveMode,
            appearance = appearance,
            onDismiss = { modeHelpOpen = false },
        )
    }

    val blockingFailure = failure?.takeIf {
        it == ConnectionFailure.BluetoothUnavailable ||
            it == ConnectionFailure.PermissionDenied ||
            it == ConnectionFailure.BluetoothDisabled ||
            it == ConnectionFailure.PairingFailed ||
            it == ConnectionFailure.IncompatibleDevice
    }
    if (state == ConnectionState.Error &&
        blockingFailure != null &&
        dismissedDialogFailure != blockingFailure
    ) {
        val confirmText = when (blockingFailure) {
            ConnectionFailure.PermissionDenied,
            ConnectionFailure.BluetoothDisabled,
            ConnectionFailure.PairingFailed -> stringResource(R.string.action_open_settings)
            ConnectionFailure.BluetoothUnavailable,
            ConnectionFailure.IncompatibleDevice -> stringResource(R.string.action_back_to_list)
            else -> stringResource(R.string.action_retry)
        }
        ClumoActionDialog(
            title = stringResource(R.string.connection_dialog_title),
            body = failureMessage,
            confirmText = confirmText,
            onConfirm = {
                dismissedDialogFailure = blockingFailure
                when (blockingFailure) {
                    ConnectionFailure.PermissionDenied -> settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                    ConnectionFailure.BluetoothDisabled -> settingsLauncher.launch(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    )
                    ConnectionFailure.PairingFailed -> settingsLauncher.launch(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    )
                    ConnectionFailure.BluetoothUnavailable,
                    ConnectionFailure.IncompatibleDevice -> onBack()
                    else -> service.registry.connect(address, scannedName)
                }
            },
            onDismiss = { dismissedDialogFailure = blockingFailure },
        )
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
private fun MenuItem(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = if (enabled) color else ClumoColors.MutedLight,
        )
    }
}

// ---------------------------------------------------------------------------
// Pomodoro
// ---------------------------------------------------------------------------

@Composable
private fun PomodoroSection(
    connection: DeviceConnection?,
    status: PomodoroStatus,
) {
    var workMin by remember { mutableIntStateOf(status.workMin.coerceIn(1, 99)) }
    var breakMin by remember { mutableIntStateOf(status.breakMin.coerceIn(1, 99)) }
    LaunchedEffect(status.workMin, status.breakMin) {
        workMin = status.workMin.coerceIn(1, 99)
        breakMin = status.breakMin.coerceIn(1, 99)
    }

    fun pushDurations() {
        connection?.pomodoroSetDurations(workMin, breakMin)
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
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (work) accents.accent else ClumoColors.BreakChipBg)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stringResource(
                    if (work) R.string.pomodoro_phase_work else R.string.pomodoro_phase_break
                ),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = if (work) accents.onAccent else ClumoColors.BreakChipFg,
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
                    label = stringResource(R.string.pomodoro_work_label),
                    value = workMin,
                    onChange = { workMin = it; pushDurations() },
                )
                DurationStepperRow(
                    label = stringResource(R.string.pomodoro_break_label),
                    value = breakMin,
                    onChange = { breakMin = it; pushDurations() },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CtaPillButton(
                text = stringResource(
                    if (status.isRunning) R.string.pomodoro_pause else R.string.pomodoro_start
                ),
                onClick = {
                    if (status.isRunning) connection?.pomodoroPause() else connection?.pomodoroStart()
                },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1.4f),
            )
            OutlinePillButton(
                text = stringResource(R.string.pomodoro_reset),
                onClick = { connection?.pomodoroReset() },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------

@Composable
private fun CountdownTimerSection(
    connection: DeviceConnection?,
    status: CountdownTimerStatus,
    completionBlinkOn: Boolean,
) {
    var minutes by remember { mutableIntStateOf(status.configuredMin.coerceIn(0, 59)) }
    var seconds by remember { mutableIntStateOf(status.configuredSec.coerceIn(0, 59)) }
    LaunchedEffect(status.configuredMin, status.configuredSec) {
        minutes = status.configuredMin.coerceIn(0, 59)
        seconds = status.configuredSec.coerceIn(0, 59)
    }

    val stateLabel = stringResource(
        when {
            status.isRunning -> R.string.timer_state_running
            status.isPaused -> R.string.timer_state_paused
            status.isCompleted -> R.string.timer_state_completed
            else -> R.string.timer_state_idle
        }
    )
    val primaryLabel = stringResource(
        when {
            status.isRunning -> R.string.timer_pause
            status.isPaused -> R.string.timer_resume
            else -> R.string.timer_start
        }
    )

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
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (status.isCompleted) ClumoColors.Coral else accents.accent)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stateLabel,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = if (status.isCompleted) ClumoColors.White else accents.onAccent,
            )
        }

        Text(
            text = status.formatRemaining(),
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.alpha(
                if (status.isCompleted && !completionBlinkOn) 0.12f else 1f
            ),
        )

        if (status.isIdle) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimerStepperRow(
                    label = stringResource(R.string.timer_minutes_label),
                    value = minutes,
                    decrementEnabled = minutes > 0 && !(minutes == 1 && seconds == 0),
                    incrementEnabled = minutes < 59,
                    onDecrement = {
                        val next = minutes - 1
                        minutes = next
                        connection?.timerSetDuration(next, seconds)
                    },
                    onIncrement = {
                        val next = minutes + 1
                        minutes = next
                        connection?.timerSetDuration(next, seconds)
                    },
                )
                TimerStepperRow(
                    label = stringResource(R.string.timer_seconds_label),
                    value = seconds,
                    decrementEnabled = seconds > 0 && !(minutes == 0 && seconds == 1),
                    incrementEnabled = seconds < 59,
                    onDecrement = {
                        val next = seconds - 1
                        seconds = next
                        connection?.timerSetDuration(minutes, next)
                    },
                    onIncrement = {
                        val next = seconds + 1
                        seconds = next
                        connection?.timerSetDuration(minutes, next)
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CtaPillButton(
                text = primaryLabel,
                onClick = {
                    if (status.isRunning) connection?.timerPause() else connection?.timerStart()
                },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1.4f),
            )
            OutlinePillButton(
                text = stringResource(R.string.timer_cancel),
                onClick = { connection?.timerCancel() },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimerStepperRow(
    label: String,
    value: Int,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
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
        StepperButton(text = "−", enabled = decrementEnabled, onClick = onDecrement)
        Text(
            text = "%02d".format(value),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(54.dp),
        )
        StepperButton(text = "＋", enabled = incrementEnabled, onClick = onIncrement)
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
            text = stringResource(R.string.pomodoro_minutes_unit),
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
    appearance: DeviceAppearance,
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
                                appearance = appearance,
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
    appearance: DeviceAppearance,
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
                frameColor = appearance.enclosureColor.toComposeColor(),
                ledColor = appearance.ledColor.toComposeColor(),
                frameOutline = when {
                    selected -> ClumoColors.Text
                    contentToneFor(appearance.enclosureColor) == ContentTone.Dark -> {
                        ClumoColors.OutlineBorder
                    }
                    else -> null
                },
                size = 96.dp,
                frameCorner = 22.dp,
                framePadding = 12.dp,
                innerCorner = 11.dp,
                gridPadding = 7.dp,
                glow = false,
            )
            if (selected) {
                val accents = LocalClumoAccents.current
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(24.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(accents.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RoundedFontFamily,
                        color = accents.onAccent,
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
private fun VisualizerSection(
    connection: DeviceConnection?,
    visualizerSensitivity: Float,
    automaticLowVolumeBoost: Boolean,
    onVisualizerSensitivityChange: (Float) -> Unit,
    onVisualizerSensitivityChangeFinished: (Float) -> Unit,
    onAutomaticLowVolumeBoostChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val vizActive = connection?.audioVisualizer?.isActive?.collectAsState()?.value ?: false
    var sensitivity by remember(visualizerSensitivity) {
        mutableFloatStateOf(visualizerSensitivity * 100f)
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
            connection.stopAudioVisualizer(clearDisplay = true)
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
            CtaPillButton(
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
                    onVisualizerSensitivityChange(it / 100f)
                },
                onValueChangeFinished = {
                    onVisualizerSensitivityChangeFinished(sensitivity / 100f)
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viz_auto_low_volume_boost),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                modifier = Modifier.weight(1f),
            )
            ClumoToggleSwitch(
                checked = automaticLowVolumeBoost,
                onCheckedChange = onAutomaticLowVolumeBoostChange,
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
