package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.PomodoroStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A decoded value received from a successful GATT read or notification. */
sealed interface DeviceObservation {
    data class Mode(val value: Int) : DeviceObservation
    data class Brightness(val value: Int) : DeviceObservation
    data class Pomodoro(val value: PomodoroStatus) : DeviceObservation
    data class Timer(val value: CountdownTimerStatus) : DeviceObservation

    /** The frame CLumo has actually committed to the matrix, not a live preview. */
    data class DisplayCommittedFrame(val value: Long) : DeviceObservation
}

/**
 * StateFlows retain the latest decoded value for initial synchronization.
 * [observations] emits every read and notification, including an unchanged
 * value that rejects optimistic user intent.
 */
interface DeviceTransport {
    val kind: StateFlow<DeviceKind> get() = defaultDeviceKind
    val standDisplay: StateFlow<StandDisplaySettings?> get() = defaultStandDisplay
    val standbyMode: StateFlow<Int?> get() = defaultStandbyMode
    val address: String
    val connectionState: StateFlow<ConnectionState>
    val connectionFailure: StateFlow<ConnectionFailure?>
    val reconnectAttempt: StateFlow<Int>
    val currentMode: StateFlow<Int?>
    val pomodoroStatus: StateFlow<PomodoroStatus?>
    val timerStatus: StateFlow<CountdownTimerStatus?>
    val displayCommittedFrame: StateFlow<Long?>
    val brightness: StateFlow<Int?>
    val deviceId: StateFlow<String?>
    val deviceName: StateFlow<String?>
    val buttonEvents: SharedFlow<ButtonEvent>
    val observations: SharedFlow<DeviceObservation>

    fun connect()
    fun disconnect()
    fun dispose()
    fun reconnectWithCacheRefresh()
    fun writeMode(mode: Int)

    /** Durable: CLumo persists the frame and confirms it over DISPLAY_FRAME's notify. */
    fun commitFrame(rowBytes: ByteArray)

    /** Volatile, write-without-response; CLumo drops it after its preview TTL. */
    fun previewFrame(rowBytes: ByteArray)

    /** Volatile, write-without-response; ignored unless CLumo is in Visualizer mode. */
    fun writeVisualizerColumns(heights: ByteArray)

    fun pomodoroSetDurations(workMin: Int, breakMin: Int)
    fun pomodoroStart()
    fun pomodoroPause()
    fun pomodoroReset()
    fun timerSetDuration(minutes: Int, seconds: Int)
    fun timerStart()
    fun timerPause()
    fun timerCancel()
    suspend fun readControlBindings(): ControlBindings = error("Control editing unsupported")
    suspend fun saveControlBindings(settings: ControlBindings): ControlBindings = error("Control editing unsupported")
    suspend fun readStandDisplay(): StandDisplaySettings = error("Display editing unsupported")
    suspend fun saveStandDisplay(settings: StandDisplaySettings): StandDisplaySettings = error("Display editing unsupported")
    fun writeStandbyMode(mode: Int) {}
    fun writeMediaVolume(percent: Int?) {}
    fun writeBrightness(level: Int)
}

private val defaultDeviceKind = MutableStateFlow(DeviceKind.CLUMO).asStateFlow()
private val defaultStandDisplay = MutableStateFlow<StandDisplaySettings?>(null).asStateFlow()
private val defaultStandbyMode = MutableStateFlow<Int?>(null).asStateFlow()
