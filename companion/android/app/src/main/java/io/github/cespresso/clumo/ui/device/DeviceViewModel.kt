package io.github.cespresso.clumo.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.session.DeviceSession
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.DeviceSessionState
import io.github.cespresso.clumo.domain.Pattern
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal suspend fun applyPatternToDevice(
    deviceId: String,
    pattern: Pattern,
    setApplied: suspend (String, String) -> Unit,
    commit: (Pattern) -> Unit,
) {
    setApplied(deviceId, pattern.id)
    commit(pattern)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModel(
    private val address: String,
    private val registry: DeviceSessionRegistry,
    private val preferences: AppPreferences,
    private val patterns: PatternRepository,
    private val repository: DeviceRepository,
) : ViewModel() {

    private val dismissedDialogFailure = MutableStateFlow<ConnectionFailure?>(null)
    private val blinkOn = MutableStateFlow(true)

    private val session: DeviceSession? get() = registry.get(address)

    private val sessionPresentation: Flow<SessionPresentation> = registry.sessions
        .map { it[address] }
        .distinctUntilChanged()
        .flatMapLatest { active ->
            if (active == null) {
                flowOf(SessionPresentation.disconnected())
            } else {
                combine(
                    active.state,
                    active.deviceId,
                    active.deviceName,
                    active.visualizerColumns,
                    active.visualizerActive,
                ) { state, id, name, columns, visualizerActive ->
                    SessionPresentation(
                        connected = true,
                        state = state,
                        deviceId = id,
                        deviceName = name,
                        columns = columns,
                        visualizerActive = visualizerActive,
                    )
                }
            }
        }

    private val identity = combine(
        repository.devices,
        preferences.aliases,
        preferences.deviceAppearances,
        preferences.primaryDeviceId,
    ) { devices, aliases, appearances, primaryId ->
        Identity(devices.firstOrNull { it.address == address }, aliases, appearances, primaryId)
    }

    private val library = patterns.patterns

    private val visualizerPreferences = combine(
        preferences.visualizerSensitivity,
        preferences.automaticLowVolumeBoost,
    ) { sensitivity, automaticBoost -> VisualizerPreferences(sensitivity, automaticBoost) }

    private val transient = combine(blinkOn, dismissedDialogFailure) { blink, dismissed ->
        Transient(blink, dismissed)
    }

    val uiState = combine(
        sessionPresentation,
        identity,
        library,
        visualizerPreferences,
        transient,
    ) { live, identity, library, visualizerPreferences, transient ->
        val observed = live.state.observed
        val stableId = live.deviceId ?: identity.knownDevice?.id
        val brightnessLevel = live.state.effectiveBrightnessLevel ?: Brightness.MAX_LEVEL
        DeviceUiStateFactory.create(
            connected = live.connected,
            state = live.state.link,
            failure = live.state.failure,
            reconnectAttempt = live.state.reconnectAttempt,
            currentMode = observed?.mode,
            pendingMode = live.state.pending.mode?.value,
            pomodoro = observed?.pomodoro,
            timer = observed?.timer,
            deviceId = stableId,
            scannedName = live.deviceName,
            knownDevice = identity.knownDevice,
            aliases = identity.aliases,
            appearances = identity.appearances,
            primaryDeviceId = identity.primaryDeviceId,
            committedFrame = live.state.effectiveCommittedFrame,
            library = library,
            brightnessUi = Brightness.toPercent(brightnessLevel),
            columns = live.columns,
            visualizerActive = live.visualizerActive,
            timerBlinkOn = transient.blinkOn,
            dismissedDialogFailure = transient.dismissedFailure,
        ).copy(
            pomodoroStatus = observed?.pomodoro ?: io.github.cespresso.clumo.domain.PomodoroStatus.DEFAULT,
            timerStatus = observed?.timer ?: io.github.cespresso.clumo.domain.CountdownTimerStatus.DEFAULT,
            brightnessLevel = brightnessLevel,
            brightnessPercent = Brightness.toPercent(brightnessLevel),
            patterns = library,
            visualizerColumns = live.columns,
            visualizerActive = live.visualizerActive,
            visualizerSensitivity = visualizerPreferences.sensitivity,
            automaticLowVolumeBoost = visualizerPreferences.automaticBoost,
            timerBlinkOn = transient.blinkOn,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DeviceUiStateFactory.initial(address),
    )

    init {
        if (registry.get(address) == null) {
            registry.connect(address, repository.getByAddress(address)?.name)
        }
        viewModelScope.launch {
            while (true) {
                delay(400)
                blinkOn.value = !blinkOn.value
            }
        }
        viewModelScope.launch {
            sessionPresentation
                .map { it.state.failure }
                .distinctUntilChanged()
                .collect { failure ->
                    if (failure == null || failure != dismissedDialogFailure.value) {
                        dismissedDialogFailure.value = null
                    }
                }
        }
    }

    fun onModeSelected(mode: Int) {
        if (mode != uiState.value.effectiveMode) session?.setMode(mode)
    }

    fun onBrightnessChanged(percent: Float) {
        session?.setBrightnessLevel(Brightness.toLevel(percent))
    }

    fun onPatternApplied(pattern: Pattern) {
        val deviceId = uiState.value.stableId ?: address
        viewModelScope.launch {
            applyPatternToDevice(
                deviceId = deviceId,
                pattern = pattern,
                setApplied = patterns::setApplied,
                commit = { session?.commitPattern(it) },
            )
        }
    }

    fun onReconnect() {
        session?.connect() ?: registry.connect(address)
    }

    fun onReconnectWithCacheRefresh() {
        session?.reconnectWithCacheRefresh() ?: registry.connect(address)
    }

    fun onDisconnect() = registry.disconnect(address)
    fun onFailureDialogDismissed(failure: ConnectionFailure) {
        dismissedDialogFailure.value = failure
    }

    fun onRename(name: String) {
        val id = uiState.value.stableId ?: return
        viewModelScope.launch { preferences.setAlias(id, name) }
    }

    fun onTogglePrimary() {
        val id = uiState.value.stableId ?: return
        viewModelScope.launch {
            preferences.setPrimaryDeviceId(if (uiState.value.isPrimary) null else id)
        }
    }

    fun onPomodoroDurationsChanged(workMin: Int, breakMin: Int) = session?.pomodoroSetDurations(workMin, breakMin)
    fun onPomodoroStart() = session?.pomodoroStart()
    fun onPomodoroPause() = session?.pomodoroPause()
    fun onPomodoroReset() = session?.pomodoroReset()
    fun onTimerDurationChanged(minutes: Int, seconds: Int) = session?.timerSetDuration(minutes, seconds)
    fun onTimerStart() = session?.timerStart()
    fun onTimerPause() = session?.timerPause()
    fun onTimerCancel() = session?.timerCancel()
    fun onVisualizerStart(): Boolean = session?.startVisualizer() == true
    fun onVisualizerStop() = session?.stopVisualizer()

    fun onVisualizerSensitivityChanged(value: Float) {
        session?.setVisualizerSensitivity(value)
    }

    fun onVisualizerSensitivityChangeFinished(value: Float) {
        viewModelScope.launch { preferences.setVisualizerSensitivity(value) }
    }

    fun onAutomaticLowVolumeBoostChanged(enabled: Boolean) {
        session?.setAutomaticLowVolumeBoost(enabled)
        viewModelScope.launch { preferences.setAutomaticLowVolumeBoost(enabled) }
    }

    private data class SessionPresentation(
        val connected: Boolean,
        val state: DeviceSessionState,
        val deviceId: String?,
        val deviceName: String?,
        val columns: IntArray,
        val visualizerActive: Boolean,
    ) {
        companion object {
            fun disconnected() =
                SessionPresentation(
                    connected = false,
                    state = DeviceSessionState(ConnectionState.Disconnected),
                    deviceId = null,
                    deviceName = null,
                    columns = IntArray(0),
                    visualizerActive = false,
                )
        }
    }

    private data class Identity(
        val knownDevice: Device?,
        val aliases: Map<String, String>,
        val appearances: Map<String, DeviceAppearance>,
        val primaryDeviceId: String?,
    )

    private data class VisualizerPreferences(
        val sensitivity: Float,
        val automaticBoost: Boolean,
    )

    private data class Transient(
        val blinkOn: Boolean,
        val dismissedFailure: ConnectionFailure?,
    )
}
