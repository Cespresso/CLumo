package io.github.cespresso.clumo.data.session

import io.github.cespresso.clumo.audio.AudioVisualizerManager
import io.github.cespresso.clumo.data.ble.ButtonEvent
import io.github.cespresso.clumo.data.ble.DeviceObservation
import io.github.cespresso.clumo.data.ble.DeviceTransport
import io.github.cespresso.clumo.data.ble.shouldStopVisualizerForLinkChange
import io.github.cespresso.clumo.data.ble.shouldStopVisualizerForModeChange
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.DeviceSessionState
import io.github.cespresso.clumo.domain.DeviceSnapshot
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.PendingCommand
import io.github.cespresso.clumo.domain.PendingCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns one CLumo link's reliable state, commands, and freshness-first streams. */
class DeviceSession internal constructor(
    private val transport: DeviceTransport,
    parentScope: CoroutineScope,
    private val nowRealtime: () -> Long,
    private val audioVisualizer: AudioVisualizerManager = AudioVisualizerManager(),
) {
    val address: String get() = transport.address
    val deviceId: StateFlow<String?> get() = transport.deviceId
    val deviceName: StateFlow<String?> get() = transport.deviceName
    val buttonEvents: SharedFlow<ButtonEvent> get() = transport.buttonEvents
    val visualizerColumns: StateFlow<IntArray> get() = audioVisualizer.columns
    val visualizerActive: StateFlow<Boolean> get() = audioVisualizer.isActive

    private val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + sessionJob)
    private val _state = MutableStateFlow(
        DeviceSessionState(link = transport.connectionState.value),
    )
    val state = _state.asStateFlow()

    private var wasReady = false
    private var modeExpiryJob: Job? = null
    private var brightnessExpiryJob: Job? = null
    private var brightnessWriteJob: Job? = null
    private var previewWriteJob: Job? = null
    private var latestPreview: ByteArray? = null
    private var visualizerWriteJob: Job? = null

    init {
        scope.launch {
            transport.connectionState.collect { link -> onLinkChanged(link) }
        }
        scope.launch {
            transport.connectionFailure.collect { failure ->
                _state.update { it.copy(failure = failure) }
            }
        }
        scope.launch {
            transport.reconnectAttempt.collect { attempt ->
                _state.update { it.copy(reconnectAttempt = attempt) }
            }
        }
        scope.launch {
            transport.observations.collect(::onObservation)
        }
    }

    private fun onLinkChanged(link: ConnectionState) {
        if (shouldStopVisualizerForLinkChange(audioVisualizer.isActive.value, link)) {
            stopVisualizer(clearDisplay = false)
        }
        if (link == ConnectionState.Ready && !wasReady) {
            val snapshot = currentSnapshotOrNull()
            val pending = _state.value.pending.expire(nowRealtime())
            _state.update {
                it.copy(
                    link = link,
                    observed = snapshot,
                    pending = pending,
                    failure = transport.connectionFailure.value,
                    reconnectAttempt = transport.reconnectAttempt.value,
                )
            }
            wasReady = true
            if (snapshot?.mode == DeviceMode.DISPLAY) {
                // Announce v2 preview support before the UI can enqueue a frame.
                transport.writeMode(DeviceMode.DISPLAY)
            }
            resend(pending)
            return
        }
        if (link != ConnectionState.Ready) wasReady = false
        _state.update {
            it.copy(
                link = link,
                failure = transport.connectionFailure.value,
                reconnectAttempt = transport.reconnectAttempt.value,
            )
        }
    }

    private fun currentSnapshotOrNull(): DeviceSnapshot? {
        val mode = transport.currentMode.value ?: return null
        val pomodoro = transport.pomodoroStatus.value ?: return null
        val timer = transport.timerStatus.value ?: return null
        val brightness = transport.brightness.value ?: return null
        val committedFrame = transport.displayCommittedFrame.value ?: return null
        return DeviceSnapshot(
            mode = mode,
            brightnessLevel = brightness,
            pomodoro = pomodoro,
            timer = timer,
            committedFrame = committedFrame,
        )
    }

    private fun onObservation(observation: DeviceObservation) {
        if (_state.value.link != ConnectionState.Ready) return
        _state.update { current ->
            val observed = current.observed ?: currentSnapshotOrNull() ?: return@update current
            when (observation) {
                is DeviceObservation.Mode -> current.copy(
                    observed = observed.copy(mode = observation.value),
                    pending = current.pending.copy(mode = null),
                )
                is DeviceObservation.Brightness -> current.copy(
                    observed = observed.copy(brightnessLevel = observation.value),
                    pending = current.pending.copy(brightnessLevel = null),
                )
                is DeviceObservation.Pomodoro -> current.copy(
                    observed = observed.copy(pomodoro = observation.value),
                )
                is DeviceObservation.Timer -> current.copy(
                    observed = observed.copy(timer = observation.value),
                )
                is DeviceObservation.DisplayCommittedFrame -> current.copy(
                    observed = observed.copy(committedFrame = observation.value),
                    pending = current.pending.copy(committedFrame = null),
                )
            }
        }
    }

    private fun resend(pending: PendingCommands) {
        pending.mode?.let { writeModeWithDisplayHandshake(it.value) }
        pending.brightnessLevel?.let { transport.writeBrightness(it.value) }
        pending.committedFrame?.let {
            transport.writeDisplay(
                Pattern.bitsToRowBytes(FaceBits.toBitsString(it.value)),
                stream = false,
            )
            transport.writeMode(DeviceMode.DISPLAY)
            transport.readDisplayCommittedFrame()
        }
    }

    private fun writeModeWithDisplayHandshake(mode: Int) {
        transport.writeMode(mode)
        if (mode == DeviceMode.DISPLAY) {
            // The first write enters Display; the same-value write enables explicit commits.
            transport.writeMode(mode)
        }
    }

    fun setMode(mode: Int) {
        if (shouldStopVisualizerForModeChange(audioVisualizer.isActive.value, mode)) {
            stopVisualizer()
        }
        val command = PendingCommand(mode, nowRealtime())
        _state.update { it.copy(pending = it.pending.copy(mode = command)) }
        writeModeWithDisplayHandshake(mode)
        modeExpiryJob?.cancel()
        modeExpiryJob = expirePendingAfterTtl()
    }

    fun setBrightnessLevel(level: Int) {
        val clamped = level.coerceIn(0, Brightness.MAX_LEVEL)
        brightnessWriteJob?.cancel()
        brightnessWriteJob = scope.launch {
            delay(BRIGHTNESS_DEBOUNCE_MS)
            val observed = _state.value.observed?.brightnessLevel
            if (observed != null && !Brightness.shouldWrite(clamped, observed)) {
                return@launch
            }
            val command = PendingCommand(clamped, nowRealtime())
            _state.update {
                it.copy(pending = it.pending.copy(brightnessLevel = command))
            }
            transport.writeBrightness(clamped)
            brightnessExpiryJob?.cancel()
            brightnessExpiryJob = expirePendingAfterTtl()
        }
    }

    private fun expirePendingAfterTtl(): Job =
        scope.launch {
            delay(PendingCommands.DEFAULT_TTL_MS)
            _state.update { it.copy(pending = it.pending.expire(nowRealtime())) }
        }

    fun pomodoroSetDurations(workMin: Int, breakMin: Int) = transport.pomodoroSetDurations(workMin, breakMin)
    fun pomodoroStart() = transport.pomodoroStart()
    fun pomodoroPause() = transport.pomodoroPause()
    fun pomodoroReset() = transport.pomodoroReset()
    fun timerSetDuration(minutes: Int, seconds: Int) = transport.timerSetDuration(minutes, seconds)
    fun timerStart() = transport.timerStart()
    fun timerPause() = transport.timerPause()
    fun timerCancel() = transport.timerCancel()

    /** Ordered v2 commit: data is previewed, then same-mode MODE write commits it. */
    fun commitPattern(pattern: Pattern) {
        cancelPreview()
        transport.writeDisplay(pattern.toRowBytes(), stream = false)
        transport.writeMode(DeviceMode.DISPLAY)
        transport.readDisplayCommittedFrame()
        _state.update {
            it.copy(
                pending = it.pending.copy(
                    committedFrame = PendingCommand(
                        FaceBits.fromBitsString(pattern.bits),
                        nowRealtime(),
                    ),
                ),
            )
        }
    }

    /**
     * Streams [bits] to DISPLAY as a live preview, re-sending the latest frame at least
     * every [PREVIEW_KEEP_ALIVE_MS] so the device's preview TTL cannot expire mid-edit.
     * Stops on [cancelPreview], [commitPattern], or the link leaving Ready.
     */
    fun previewFrame(bits: String) {
        latestPreview = Pattern.bitsToRowBytes(bits)
        if (previewWriteJob?.isActive == true) return
        previewWriteJob = scope.launch {
            var lastSent: ByteArray? = null
            var msSinceSend = 0L
            while (_state.value.link == ConnectionState.Ready) {
                val fresh = latestPreview
                latestPreview = null
                val due = fresh ?: lastSent?.takeIf { msSinceSend >= PREVIEW_KEEP_ALIVE_MS }
                if (due != null) {
                    lastSent = due
                    msSinceSend = 0L
                    transport.writeDisplay(due, stream = true)
                }
                delay(PREVIEW_INTERVAL_MS)
                msSinceSend += PREVIEW_INTERVAL_MS
            }
        }
    }

    /** Stops the live preview; the device's own TTL then reverts to the committed frame. */
    fun cancelPreview() {
        previewWriteJob?.cancel()
        previewWriteJob = null
        latestPreview = null
    }

    fun startVisualizer(): Boolean {
        if (_state.value.link != ConnectionState.Ready ||
            _state.value.effectiveMode != DeviceMode.VISUALIZER ||
            !audioVisualizer.start()
        ) {
            return false
        }
        visualizerWriteJob?.cancel()
        visualizerWriteJob = scope.launch {
            while (true) {
                delay(VISUALIZER_INTERVAL_MS)
                if (_state.value.link == ConnectionState.Ready &&
                    _state.value.effectiveMode == DeviceMode.VISUALIZER
                ) {
                    val columns = audioVisualizer.columns.value
                    transport.writeDisplay(
                        ByteArray(8) { columns.getOrElse(it) { 0 }.toByte() },
                        stream = true,
                    )
                }
            }
        }
        return true
    }

    fun stopVisualizer() = stopVisualizer(clearDisplay = true)

    private fun stopVisualizer(clearDisplay: Boolean) {
        visualizerWriteJob?.cancel()
        visualizerWriteJob = null
        audioVisualizer.stop()
        if (clearDisplay && _state.value.link == ConnectionState.Ready &&
            _state.value.effectiveMode == DeviceMode.VISUALIZER
        ) {
            transport.writeDisplay(ByteArray(8), stream = true)
        }
    }

    fun setVisualizerSensitivity(value: Float) {
        audioVisualizer.sensitivity = value.coerceIn(0f, 1f)
    }

    fun setAutomaticLowVolumeBoost(enabled: Boolean) {
        audioVisualizer.automaticLowVolumeBoost = enabled
    }

    fun connect() = transport.connect()
    fun disconnect() = transport.disconnect()
    fun reconnectWithCacheRefresh() = transport.reconnectWithCacheRefresh()

    fun dispose() {
        stopVisualizer()
        transport.dispose()
        scope.cancel()
    }

    private companion object {
        const val BRIGHTNESS_DEBOUNCE_MS = 100L
        const val PREVIEW_INTERVAL_MS = 100L

        // Must stay under the firmware's DISPLAY preview TTL, or the device reverts to
        // the committed frame mid-edit.
        const val PREVIEW_KEEP_ALIVE_MS = 2_000L
        const val VISUALIZER_INTERVAL_MS = 80L
    }
}
