package io.github.cespresso.clumo.ui.device

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.backgroundCountdownsFor
import io.github.cespresso.clumo.ui.components.ClumoActionDialog
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.components.ModeHelpDialog
import io.github.cespresso.clumo.ui.components.ModeHelpHeader
import io.github.cespresso.clumo.ui.components.NameInputDialog
import io.github.cespresso.clumo.ui.components.product.FloorSettingsState
import io.github.cespresso.clumo.ui.components.product.MatrixPage
import io.github.cespresso.clumo.ui.components.product.ProductDisplayState
import io.github.cespresso.clumo.ui.components.product.StandPart
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.delay

@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: (deviceId: String) -> Unit,
    onOpenEditor: (patternId: String?) -> Unit,
    modifier: Modifier = Modifier,
    matrixPage: MatrixPage = MatrixPage.Overview,
    onMatrixSelect: (MatrixPage) -> Unit = {},
    onLeaveDevice: () -> Unit = onBack,
    onStandbyPreview: (Long?) -> Unit = {},
    selectedPart: StandPart? = null,
    floorSettings: FloorSettingsState? = null,
    onPartSelect: (StandPart?) -> Unit = {},
    onPartBusyChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    // Keep the shared scene visible; the placeholder kind must not mount another device's controls.
    if (!ui.initialized) return
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onReconnect()
    }
    val state = ui.link
    val reconnectAttempt = ui.reconnectAttempt
    val effectiveMode = ui.effectiveMode
    val pomodoroStatus = ui.pomodoroStatus
    val timerStatus = ui.timerStatus
    val patterns = ui.patterns
    val shownPatternId = ui.shownPatternId
    val timerBlinkOn = ui.timerBlinkOn

    var brightnessUi by remember { mutableFloatStateOf(ui.brightnessPercent) }
    var brightnessDragging by remember { mutableStateOf(false) }
    LaunchedEffect(ui.brightnessPercent) {
        // Adopt the device's value, but never yank the slider out from under a finger.
        if (!brightnessDragging) brightnessUi = ui.brightnessPercent
    }
    val ready = ui.ready
    val spatialStand = ui.kind == DeviceKind.HEADPHONE_STAND
    var partBusy by remember { mutableStateOf(false) }
    val stableId = ui.stableId
    val appearance = ui.appearance
    val displayName = ui.displayName
    val stateLabel = ui.stateLabel.text(ui.reconnectAttempt)
    val stateLabelColor = when (ui.stateTone) {
        DeviceStateTone.Accent -> LocalClumoAccents.current.accent
        DeviceStateTone.Error -> ClumoColors.Coral
        DeviceStateTone.Muted -> ClumoColors.Muted
    }
    val failureMessage = ui.failureMessage.text()

    fun runFailureAction(action: DeviceFailureAction) {
        when (action) {
            DeviceFailureAction.OpenAppSettings -> settingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                ),
            )
            DeviceFailureAction.OpenBluetoothSettings -> settingsLauncher.launch(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            )
            DeviceFailureAction.BackToList -> onLeaveDevice()
            DeviceFailureAction.Retry -> viewModel.onReconnect()
        }
    }

    var standEditorOpen by androidx.compose.runtime.saveable.rememberSaveable(stableId) { mutableStateOf(false) }
    if (standEditorOpen) {
        StandDisplayEditor(
            onPreview = onStandbyPreview,
            readSettings = viewModel::readStandDisplay,
            spatial = spatialStand,
            ready = ready,
            accent = Color(appearance.buttonBColor.value or 0xFF000000.toInt()),
            saveSettings = viewModel::saveStandDisplay,
            onClose = { standEditorOpen = false },
        )
    }

    if (spatialStand && floorSettings != null) {
        StandFloorSettings(
            state = floorSettings, part = selectedPart, ready = ready, brightness = brightnessUi,
            connectionLabel = stateLabel,
            onBrightness = {
                brightnessDragging = true
                brightnessUi = it
                viewModel.onBrightnessChanged(it)
            },
            onEditDisplay = { standEditorOpen = true },
            onClose = onBack, onReconnect = { runFailureAction(ui.bannerAction) },
            read = viewModel::readControlBindings, save = viewModel::saveControlBindings,
            onBusy = {
                partBusy = it
                onPartBusyChange(it)
            },
        )
        LaunchedEffect(brightnessUi) {
            delay(300)
            brightnessDragging = false
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var forgetOpen by remember { mutableStateOf(false) }
    var modeHelpOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("device-screen")) {
        val landscape = maxWidth > maxHeight
        val screenHeight = maxHeight
        if (spatialStand && floorSettings?.floorUi == false && selectedPart != null) {
            AnchoredPartControls(
                state = floorSettings,
                accent = Color(
                    (
                        if (selectedPart == StandPart.Matrix) {
                            appearance.buttonBColor.value
                        } else {
                            ProductDisplayState(appearance = appearance).regionColors()[selectedPart.nodeName]
                                ?: appearance.panelColor.value
                        }
                        ) or 0xFF000000.toInt(),
                ),
                modifier = Modifier.fillMaxSize(),
                displayControl = {
                    StandVisualizerControl(
                        active = ui.visualizerActive,
                        ready = ready && floorSettings.cameraSettled,
                        accent = Color(appearance.buttonBColor.value or 0xFF000000.toInt()),
                        onStart = viewModel::onVisualizerStart,
                        onStop = viewModel::onVisualizerStop,
                        showHeading = false,
                    )
                },
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (floorSettings?.snapshot?.choosing != true) {
                DeviceTopBar(
                    title = displayName,
                    connection = state,
                    failureMessage = failureMessage,
                    recoveryLabel = when (ui.bannerAction) {
                        DeviceFailureAction.OpenAppSettings -> "Permissions"
                        DeviceFailureAction.OpenBluetoothSettings -> "Bluetooth settings"
                        DeviceFailureAction.BackToList -> "Back to devices"
                        DeviceFailureAction.Retry -> "Reconnect"
                    },
                    onRecover = { if (!partBusy) runFailureAction(ui.bannerAction) },
                    onBack = {
                        if (!partBusy) {
                            onBack()
                        }
                    },
                    onToggleMenu = { if (!partBusy) menuOpen = !menuOpen },
                )
            }

            Spacer(Modifier.weight(1f))
            if (spatialStand && floorSettings != null) {
                if (!floorSettings.floorUi && selectedPart == null) {
                    StandOverview(
                        ui,
                        floorSettings.snapshot.overviewAssignments,
                        onPartSelect,
                        enabled = !partBusy,
                        modifier = Modifier.align(Alignment.End)
                            .fillMaxWidth(if (landscape) .46f else 1f),
                    )
                }
            } else if (spatialStand) {
                Column(
                    Modifier.align(Alignment.End).fillMaxWidth(if (landscape) .43f else 1f)
                        .height(screenHeight * if (landscape) .65f else .42f)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state == ConnectionState.Bonding) PairingHintBanner()
                    if (state == ConnectionState.Error || state == ConnectionState.Reconnecting) {
                        ConnectionTroubleBanner(
                            reconnecting = state == ConnectionState.Reconnecting,
                            reconnectAttempt = reconnectAttempt,
                            failureMessage = failureMessage,
                            action = ui.bannerAction.takeIf { state == ConnectionState.Error },
                            onAction = { runFailureAction(ui.bannerAction) },
                        )
                    } else if (!ready) {
                        Text(stateLabel, color = stateLabelColor, fontSize = 12.sp)
                    }
                    StandPartControls(
                        part = selectedPart, ready = ready, brightness = brightnessUi,
                        onBrightnessChange = {
                            brightnessDragging = true
                            brightnessUi = it
                            viewModel.onBrightnessChanged(it)
                        },
                        onEditDisplay = { standEditorOpen = true },
                        onClose = { if (!partBusy) onPartSelect(null) },
                        readSettings = viewModel::readControlBindings, saveSettings = viewModel::saveControlBindings,
                        onBusyChange = {
                            partBusy = it
                            onPartBusyChange(it)
                        },
                    )
                    LaunchedEffect(brightnessUi) {
                        delay(300)
                        brightnessDragging = false
                    }
                }
            } else if (matrixPage == MatrixPage.Overview) {
                MatrixOverview(ui, onMatrixSelect, Modifier.align(Alignment.End).fillMaxWidth(if (landscape) .46f else 1f))
            } else {
                key(matrixPage) {
                    Column(
                        modifier = Modifier.align(Alignment.End)
                            .fillMaxWidth(if (landscape) .43f else 1f)
                            .heightIn(max = screenHeight * if (landscape) .65f else .42f)
                            .background(Color.White.copy(alpha = .96f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .testTag("device-controls")
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

                        if (ready && ui.kind == DeviceKind.CLUMO) {
                            backgroundCountdownsFor(effectiveMode, pomodoroStatus, timerStatus)
                                .forEach { countdown ->
                                    BackgroundCountdownBanner(
                                        countdown = countdown,
                                        pomodoro = pomodoroStatus,
                                        timer = timerStatus,
                                        onPomodoroReset = viewModel::onPomodoroReset,
                                        onTimerCancel = viewModel::onTimerCancel,
                                    )
                                }
                        }

                        when (matrixPage) {
                            MatrixPage.Display -> MatrixDisplaySettings(
                                brightnessUi,
                                effectiveMode,
                                ready,
                                onBrightness = {
                                    brightnessUi = it
                                    viewModel.onBrightnessChanged(it)
                                },
                                onMode = viewModel::onModeSelected,
                            )
                            MatrixPage.MainButton, MatrixPage.SubButton -> MatrixButtonInfo(ui, matrixPage)
                            MatrixPage.Mode -> {
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
                                            DeviceMode.DISPLAY -> PatternsSection(
                                                patterns = patterns,
                                                selectedId = shownPatternId,
                                                appearance = appearance,
                                                onSelect = viewModel::onPatternApplied,
                                                onAddNew = { onOpenEditor(null) },
                                                onEdit = {
                                                    shownPatternId?.let { onOpenEditor(it) }
                                                },
                                            )

                                            DeviceMode.VISUALIZER -> VisualizerSection(
                                                visualizerActive = ui.visualizerActive,
                                                visualizerSensitivity = ui.visualizerSensitivity,
                                                automaticLowVolumeBoost = ui.automaticLowVolumeBoost,
                                                onStart = { viewModel.onVisualizerStart() },
                                                onStop = viewModel::onVisualizerStop,
                                                onVisualizerSensitivityChange =
                                                viewModel::onVisualizerSensitivityChanged,
                                                onVisualizerSensitivityChangeFinished =
                                                viewModel::onVisualizerSensitivityChangeFinished,
                                                onAutomaticLowVolumeBoostChange =
                                                viewModel::onAutomaticLowVolumeBoostChanged,
                                                hardwareMediaControls = ui.kind == DeviceKind.HEADPHONE_STAND,
                                                ready = ready,
                                            )

                                            DeviceMode.POMODORO -> PomodoroSection(
                                                status = pomodoroStatus,
                                                onDurationsChange = viewModel::onPomodoroDurationsChanged,
                                                onStart = viewModel::onPomodoroStart,
                                                onPause = viewModel::onPomodoroPause,
                                                onReset = viewModel::onPomodoroReset,
                                            )

                                            DeviceMode.TIMER -> CountdownTimerSection(
                                                status = timerStatus,
                                                completionBlinkOn = timerBlinkOn,
                                                onDurationChange = viewModel::onTimerDurationChanged,
                                                onStart = viewModel::onTimerStart,
                                                onPause = viewModel::onTimerPause,
                                                onCancel = viewModel::onTimerCancel,
                                            )

                                            else -> Unit
                                        }
                                    }
                                }
                            }
                            MatrixPage.Overview -> Unit
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
                onTogglePrimary = viewModel::onTogglePrimary,
                onSettings = onOpenSettings,
                onRefreshGatt = viewModel::onReconnectWithCacheRefresh,
                onDisconnect = {
                    viewModel.onDisconnect()
                    onLeaveDevice()
                },
                onForget = { forgetOpen = true },
            )
        }
    }

    if (forgetOpen) {
        ClumoActionDialog(
            title = stringResource(R.string.device_forget_confirm_title),
            body = stringResource(R.string.device_forget_confirm_body),
            confirmText = stringResource(R.string.device_forget_confirm),
            onConfirm = {
                forgetOpen = false
                viewModel.onForgetDevice()
                onLeaveDevice()
            },
            onDismiss = { forgetOpen = false },
        )
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
                viewModel.onFailureDialogDismissed(dialog.failure)
                runFailureAction(dialog.confirm)
            },
            onDismiss = { viewModel.onFailureDialogDismissed(dialog.failure) },
        )
    }

    if (renameOpen) {
        NameInputDialog(
            title = stringResource(R.string.rename_title),
            initialValue = displayName,
            placeholder = null,
            onConfirm = { name ->
                renameOpen = false
                viewModel.onRename(name)
            },
            onDismiss = { renameOpen = false },
        )
    }
}
