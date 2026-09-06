package io.github.cespresso.clumo.data.ble

import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.PomodoroStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A decoded value received from a successful GATT read or notification. */
sealed interface DeviceObservation {
    data class Mode(val value: Int) : DeviceObservation
    data class Brightness(val value: Int) : DeviceObservation
    data class Pomodoro(val value: PomodoroStatus) : DeviceObservation
    data class Timer(val value: CountdownTimerStatus) : DeviceObservation
}

/**
 * StateFlows retain the latest decoded value for initial synchronization.
 * [observations] emits every read and notification, including an unchanged
 * value that rejects optimistic user intent.
 */
interface DeviceTransport {
    val address: String
    val connectionState: StateFlow<ConnectionState>
    val connectionFailure: StateFlow<ConnectionFailure?>
    val reconnectAttempt: StateFlow<Int>
    val currentMode: StateFlow<Int?>
    val pomodoroStatus: StateFlow<PomodoroStatus?>
    val timerStatus: StateFlow<CountdownTimerStatus?>
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
    fun writeDisplay(data: ByteArray, stream: Boolean = false)
    fun pomodoroSetDurations(workMin: Int, breakMin: Int)
    fun pomodoroStart()
    fun pomodoroPause()
    fun pomodoroReset()
    fun timerSetDuration(minutes: Int, seconds: Int)
    fun timerStart()
    fun timerPause()
    fun timerCancel()
    fun writeBrightness(level: Int)
}
