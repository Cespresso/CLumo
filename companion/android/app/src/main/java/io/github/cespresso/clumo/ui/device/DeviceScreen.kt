package io.github.cespresso.clumo.ui.device

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.backgroundCountdownsFor
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

@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: (deviceId: String) -> Unit,
    onOpenEditor: (patternId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
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
    val selectedPatternId = ui.shownPatternId
    val timerBlinkOn = ui.timerBlinkOn

    var brightnessUi by remember { mutableFloatStateOf(ui.brightnessPercent) }
    var brightnessDragging by remember { mutableStateOf(false) }
    LaunchedEffect(ui.brightnessPercent) {
        // Adopt the device's value, but never yank the slider out from under a finger.
        if (!brightnessDragging) brightnessUi = ui.brightnessPercent
    }
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
            DeviceFailureAction.BackToList -> onBack()
            DeviceFailureAction.Retry -> viewModel.onReconnect()
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var modeHelpOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
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

                if (ready) {
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
                                        viewModel.onBrightnessChanged(it)
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
                                    viewModel.onModeSelected(index)
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
                                DeviceMode.DISPLAY -> PatternsSection(
                                    patterns = patterns,
                                    selectedId = selectedPatternId,
                                    appearance = appearance,
                                    onSelect = viewModel::onPatternApplied,
                                    onAddNew = { onOpenEditor(null) },
                                    onEdit = {
                                        selectedPatternId?.let { onOpenEditor(it) }
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
