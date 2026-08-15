package io.github.cespresso.clumo.ui.device

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRegistry
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.ui.components.ClumoActionDialog
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.components.ModeHelpDialog
import io.github.cespresso.clumo.ui.components.ModeHelpHeader
import io.github.cespresso.clumo.ui.components.NameInputDialog
import io.github.cespresso.clumo.ui.components.SegmentedControl
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DeviceScreen(
    registry: DeviceRegistry,
    repository: DeviceRepository,
    preferences: AppPreferences,
    patternRepository: PatternRepository,
    address: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: (deviceId: String) -> Unit,
    onOpenEditor: (patternId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connections by registry.connections.collectAsState()
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

    val aliases by preferences.aliases.collectAsState(initial = emptyMap())
    val appearances by preferences.deviceAppearances.collectAsState(initial = emptyMap())
    val primaryDeviceId by preferences.primaryDeviceId.collectAsState(initial = null)
    val visualizerSensitivity by preferences.visualizerSensitivity.collectAsState(initial = 0.6f)
    val automaticLowVolumeBoost by preferences.automaticLowVolumeBoost.collectAsState(initial = false)
    val patterns by patternRepository.patterns.collectAsState(initial = emptyList())
    val selectedPatternId by patternRepository.selectedId.collectAsState(initial = null)
    val selectedPattern = patterns.firstOrNull { it.id == selectedPatternId }

    val knownDevice = repository.getByAddress(address)

    // Sync the segmented selector with the device (mode notifications included).
    var pendingMode by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentMode) { if (currentMode != null) pendingMode = null }
    val effectiveMode = effectiveModeOf(pendingMode, currentMode)

    // Brightness: the slider runs 0..100, the device takes 0..15, and a level is written only
    // once it has held still for ~100ms.
    val deviceBrightness = connection?.brightness?.collectAsState()?.value ?: Brightness.MAX_LEVEL
    var brightnessUi by remember { mutableFloatStateOf(Brightness.toPercent(deviceBrightness)) }
    var brightnessDragging by remember { mutableStateOf(false) }
    LaunchedEffect(deviceBrightness) {
        // Adopt the device's value, but never yank the slider out from under a finger.
        if (!brightnessDragging) brightnessUi = Brightness.toPercent(deviceBrightness)
    }
    LaunchedEffect(connection) {
        if (connection == null) return@LaunchedEffect
        snapshotFlow { Brightness.toLevel(brightnessUi) }
            .distinctUntilChanged()
            .collectLatest { level ->
                delay(100)
                if (Brightness.shouldWrite(level, connection.brightness.value)) {
                    connection.writeBrightness(level)
                }
            }
    }

    val timerBlinkOn = completionBlink(
        effectiveMode == BleUuids.MODE_TIMER && timerStatus?.isCompleted == true
    )
    val columns = connection?.audioVisualizer?.columns?.collectAsState()?.value ?: IntArray(0)
    val visualizerActive = connection?.audioVisualizer?.isActive?.collectAsState()?.value ?: false

    var dismissedDialogFailure by remember { mutableStateOf<ConnectionFailure?>(null) }
    LaunchedEffect(failure) {
        if (failure == null) dismissedDialogFailure = null
    }

    val ui = DeviceUiStateFactory.create(
        connected = connection != null,
        state = state,
        failure = failure,
        reconnectAttempt = reconnectAttempt,
        currentMode = currentMode,
        pendingMode = pendingMode,
        pomodoro = pomodoroStatus,
        timer = timerStatus,
        deviceId = deviceId,
        scannedName = scannedName,
        knownDevice = knownDevice,
        aliases = aliases,
        appearances = appearances,
        primaryDeviceId = primaryDeviceId,
        selectedPatternBits = selectedPattern?.bits,
        brightnessUi = brightnessUi,
        columns = columns,
        visualizerActive = visualizerActive,
        timerBlinkOn = timerBlinkOn,
        dismissedDialogFailure = dismissedDialogFailure,
    )
    val ready = ui.ready
    val stableId = ui.stableId
    val appearance = ui.appearance
    val displayName = ui.displayName
    val mirrorBits = ui.mirrorBits
    val litAlpha = ui.litAlpha
    val stateLabel = ui.stateLabel.text(ui.reconnectAttempt)
    val stateLabelColor = when (ui.stateTone) {
        DeviceStateTone.Accent -> LocalClumoAccents.current.accent
        DeviceStateTone.Error -> ClumoColors.Coral
        DeviceStateTone.Muted -> ClumoColors.Muted
    }
    val failureMessage = ui.failureMessage.text()

    // Re-read MODE when entering the screen so the selector starts in sync.
    LaunchedEffect(connection, ready) {
        if (ready) connection?.readMode()
    }

    // While in Display mode, keep the device showing the selected pattern.
    LaunchedEffect(ready, effectiveMode, selectedPattern?.bits, connection) {
        if (ready && effectiveMode == BleUuids.MODE_DISPLAY && selectedPattern != null) {
            connection?.writeDisplay(selectedPattern.toRowBytes())
        }
    }

    fun runFailureAction(action: DeviceFailureAction) {
        when (action) {
            DeviceFailureAction.OpenAppSettings -> settingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            )
            DeviceFailureAction.OpenBluetoothSettings -> settingsLauncher.launch(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
            DeviceFailureAction.BackToList -> onBack()
            DeviceFailureAction.Retry -> registry.connect(address, scannedName)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var modeHelpOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DeviceTopBar(
                title = displayName,
                onBack = onBack,
                onToggleMenu = { menuOpen = !menuOpen },
            )

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
                    PairingHintBanner()
                }

                if (state == ConnectionState.Error || state == ConnectionState.Reconnecting) {
                    ConnectionTroubleBanner(
                        reconnecting = state == ConnectionState.Reconnecting,
                        reconnectAttempt = reconnectAttempt,
                        failureMessage = failureMessage,
                        action = ui.bannerAction.takeIf { state == ConnectionState.Error },
                        onAction = { runFailureAction(ui.bannerAction) },
                    )
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
                    ReadyGate(ready) {
                        Column {
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
                    }

                    ModeHelpHeader(
                        mode = effectiveMode,
                        onHelpClick = { modeHelpOpen = true },
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                    )

                    // The mode-specific controls
                    ReadyGate(
                        ready = ready,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 2.dp),
                    ) {
                        Box {
                            when (effectiveMode) {
                                BleUuids.MODE_DISPLAY -> PatternsSection(
                                    patterns = patterns,
                                    selectedId = selectedPatternId,
                                    appearance = appearance,
                                    onSelect = { pattern ->
                                        scope.launch { patternRepository.select(pattern.id) }
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
                                            preferences.setVisualizerSensitivity(sensitivity)
                                        }
                                    },
                                    onAutomaticLowVolumeBoostChange = { enabled ->
                                        connection?.audioVisualizer?.automaticLowVolumeBoost = enabled
                                        scope.launch {
                                            preferences.setAutomaticLowVolumeBoost(enabled)
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
                    }
                }
            }
        }

        if (menuOpen) {
            DeviceMenu(
                // The entries that write something per-device need an id to write it against.
                identified = stableId != null,
                isPrimary = ui.isPrimary,
                onDismiss = { menuOpen = false },
                onRename = { renameOpen = true },
                onAppearance = { stableId?.let(onOpenAppearance) },
                onTogglePrimary = {
                    stableId?.let { id ->
                        scope.launch {
                            preferences.setPrimaryDeviceId(if (ui.isPrimary) null else id)
                        }
                    }
                },
                onSettings = onOpenSettings,
                onRefreshGatt = {
                    registry.get(address)?.reconnectWithCacheRefresh()
                        ?: registry.connect(address, scannedName)
                },
                onDisconnect = {
                    registry.disconnect(address)
                    onBack()
                },
            )
        }
    }

    if (modeHelpOpen) {
        ModeHelpDialog(
            mode = effectiveMode,
            appearance = appearance,
            onDismiss = { modeHelpOpen = false },
        )
    }

    ui.dialog?.let { dialog ->
        ClumoActionDialog(
            title = stringResource(R.string.connection_dialog_title),
            body = dialog.message.text(),
            confirmText = dialog.confirm.label(),
            onConfirm = {
                dismissedDialogFailure = dialog.failure
                runFailureAction(dialog.confirm)
            },
            onDismiss = { dismissedDialogFailure = dialog.failure },
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
                scope.launch { preferences.setAlias(id, name) }
            },
            onDismiss = { renameOpen = false },
        )
    }
}
