package io.github.cespresso.clumo.ui.device

import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.DeviceNaming
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.effectiveModeOf
import io.github.cespresso.clumo.domain.mirrorBitsFor
import io.github.cespresso.clumo.domain.resolveAppearance

/** The connection line under the device figure. Resolved to a string by the screen. */
enum class DeviceStateLabel {
    Connecting,
    Reconnecting,
    Pairing,
    Discovering,
    Synchronizing,
    Connected,
    Error,
    Disconnected,
}

/** How that line reads: the accent when the link is good, coral when it is not. */
enum class DeviceStateTone { Accent, Error, Muted }

/** Why a connection attempt stopped, as the screen words it. */
enum class DeviceFailureMessage {
    Unavailable,
    BluetoothOff,
    Permission,
    Timeout,
    Pairing,
    Services,
    Incompatible,
    Sync,
    /** Also covers a failure the link never reported. */
    Lost,
}

/**
 * What the single button on a failure offers to do. Two of these carry the same label and
 * differ only in where they land, because a failed pairing is repaired in the Bluetooth
 * screen rather than in the app's own settings page.
 */
enum class DeviceFailureAction {
    OpenAppSettings,
    OpenBluetoothSettings,
    BackToList,
    Retry,
}

/** A failure the screen cannot retry past, so it interrupts rather than sits in a banner. */
data class DeviceFailureDialog(
    val failure: ConnectionFailure,
    val message: DeviceFailureMessage,
    val confirm: DeviceFailureAction,
)

/**
 * Everything the device screen draws, with no Compose or Android type in sight. Text is named
 * rather than resolved so the screen owns the strings and this owns the rules.
 */
data class DeviceUiState(
    val displayName: String,
    val stableId: String?,
    val isPrimary: Boolean,
    val appearance: DeviceAppearance,
    val ready: Boolean,
    val effectiveMode: Int,
    val stateLabel: DeviceStateLabel,
    val stateTone: DeviceStateTone,
    val reconnectAttempt: Int,
    val failureMessage: DeviceFailureMessage,
    val bannerAction: DeviceFailureAction,
    val dialog: DeviceFailureDialog?,
    val mirrorBits: Long,
    val litAlpha: Float,
    val link: ConnectionState = ConnectionState.Disconnected,
    val pomodoroStatus: PomodoroStatus = PomodoroStatus.DEFAULT,
    val timerStatus: CountdownTimerStatus = CountdownTimerStatus.DEFAULT,
    val brightnessLevel: Int = Brightness.MAX_LEVEL,
    val brightnessPercent: Float = Brightness.toPercent(Brightness.MAX_LEVEL),
    val patterns: List<Pattern> = emptyList(),
    val appliedPatternId: String? = null,
    val visualizerColumns: IntArray = IntArray(0),
    val visualizerActive: Boolean = false,
    val visualizerSensitivity: Float = 0.6f,
    val automaticLowVolumeBoost: Boolean = false,
    val timerBlinkOn: Boolean = true,
)

/**
 * Builds the whole visible state of the device screen. Every input is a parameter, the blink
 * phase included, so each state it can produce is unit testable.
 */
/**
 * The mode the selector shows: an unconfirmed tap, else what the device last reported, else
 * Pomodoro. Exposed because the screen needs it before the state is built, to decide whether
 * a completed timer should be blinking.
 */
object DeviceUiStateFactory {

    fun initial(displayName: String): DeviceUiState = create(
        connected = false,
        state = ConnectionState.Disconnected,
        failure = null,
        reconnectAttempt = 0,
        currentMode = null,
        pendingMode = null,
        pomodoro = null,
        timer = null,
        deviceId = null,
        scannedName = null,
        knownDevice = null,
        aliases = emptyMap(),
        appearances = emptyMap(),
        primaryDeviceId = null,
        selectedPatternBits = null,
        committedFrame = null,
        brightnessUi = Brightness.toPercent(Brightness.MAX_LEVEL),
        columns = IntArray(0),
        visualizerActive = false,
        timerBlinkOn = true,
        dismissedDialogFailure = null,
    ).copy(displayName = displayName)

    /** The five failures no amount of retrying from this screen can clear. */
    private val BLOCKING = setOf(
        ConnectionFailure.BluetoothUnavailable,
        ConnectionFailure.PermissionDenied,
        ConnectionFailure.BluetoothDisabled,
        ConnectionFailure.PairingFailed,
        ConnectionFailure.IncompatibleDevice,
    )

    @Suppress("LongParameterList")
    fun create(
        connected: Boolean,
        state: ConnectionState,
        failure: ConnectionFailure?,
        reconnectAttempt: Int,
        currentMode: Int?,
        pendingMode: Int?,
        pomodoro: PomodoroStatus?,
        timer: CountdownTimerStatus?,
        deviceId: String?,
        scannedName: String?,
        knownDevice: Device?,
        aliases: Map<String, String>,
        appearances: Map<String, DeviceAppearance>,
        primaryDeviceId: String?,
        selectedPatternBits: String?,
        committedFrame: Long?,
        brightnessUi: Float,
        columns: IntArray,
        visualizerActive: Boolean,
        timerBlinkOn: Boolean,
        dismissedDialogFailure: ConnectionFailure?,
    ): DeviceUiState {
        // The id the firmware reports outranks the stored one: a device that re-paired under a
        // new MAC is still the same device, and the link is the fresher source.
        val stableId = deviceId ?: knownDevice?.id
        val ready = state == ConnectionState.Ready
        val effectiveMode = effectiveModeOf(pendingMode, currentMode)

        val blocking = failure?.takeIf { it in BLOCKING }
        val dialog = blocking
            ?.takeIf { state == ConnectionState.Error && dismissedDialogFailure != it }
            ?.let {
                DeviceFailureDialog(
                    failure = it,
                    message = failureMessageFor(it),
                    confirm = dialogActionFor(it),
                )
            }

        return DeviceUiState(
            displayName = DeviceNaming.displayName(
                deviceId = stableId,
                aliases = aliases,
                scannedName = scannedName,
                fallbackName = knownDevice?.fallbackName,
            ),
            stableId = stableId,
            isPrimary = stableId != null && stableId == primaryDeviceId,
            appearance = resolveAppearance(stableId, appearances),
            ready = ready,
            effectiveMode = effectiveMode,
            stateLabel = stateLabelFor(state),
            stateTone = when (state) {
                ConnectionState.Ready -> DeviceStateTone.Accent
                ConnectionState.Error -> DeviceStateTone.Error
                else -> DeviceStateTone.Muted
            },
            reconnectAttempt = reconnectAttempt,
            failureMessage = failureMessageFor(failure),
            bannerAction = bannerActionFor(failure),
            dialog = dialog,
            mirrorBits = if (!connected || !ready) {
                FaceBits.EMPTY
            } else {
                mirrorBitsFor(
                    // The device's own mode, not the selector's. The mirror reports what the
                    // LED is showing, and during an unconfirmed tap that is still the old mode.
                    mode = currentMode,
                    pomodoro = pomodoro,
                    timer = timer,
                    selectedPatternBits = selectedPatternBits,
                    committedFrame = committedFrame,
                    columns = columns,
                    visualizerActive = visualizerActive,
                    timerBlinkOn = timerBlinkOn,
                )
            },
            litAlpha = Brightness.litAlphaForPercent(brightnessUi),
            link = state,
        )
    }

    private fun stateLabelFor(state: ConnectionState): DeviceStateLabel = when (state) {
        ConnectionState.Connecting -> DeviceStateLabel.Connecting
        ConnectionState.Reconnecting -> DeviceStateLabel.Reconnecting
        ConnectionState.Bonding -> DeviceStateLabel.Pairing
        ConnectionState.Connected -> DeviceStateLabel.Discovering
        ConnectionState.Synchronizing -> DeviceStateLabel.Synchronizing
        ConnectionState.Ready -> DeviceStateLabel.Connected
        ConnectionState.Error -> DeviceStateLabel.Error
        ConnectionState.Disconnected -> DeviceStateLabel.Disconnected
    }

    private fun failureMessageFor(failure: ConnectionFailure?): DeviceFailureMessage = when (failure) {
        ConnectionFailure.BluetoothUnavailable -> DeviceFailureMessage.Unavailable
        ConnectionFailure.BluetoothDisabled -> DeviceFailureMessage.BluetoothOff
        ConnectionFailure.PermissionDenied -> DeviceFailureMessage.Permission
        ConnectionFailure.ConnectionTimedOut -> DeviceFailureMessage.Timeout
        ConnectionFailure.PairingFailed -> DeviceFailureMessage.Pairing
        ConnectionFailure.ServiceDiscoveryFailed -> DeviceFailureMessage.Services
        ConnectionFailure.IncompatibleDevice -> DeviceFailureMessage.Incompatible
        ConnectionFailure.SynchronizationFailed -> DeviceFailureMessage.Sync
        ConnectionFailure.ConnectionLost, null -> DeviceFailureMessage.Lost
    }

    private fun bannerActionFor(failure: ConnectionFailure?): DeviceFailureAction = when (failure) {
        ConnectionFailure.PermissionDenied -> DeviceFailureAction.OpenAppSettings
        ConnectionFailure.BluetoothDisabled -> DeviceFailureAction.OpenBluetoothSettings
        else -> DeviceFailureAction.Retry
    }

    private fun dialogActionFor(failure: ConnectionFailure): DeviceFailureAction = when (failure) {
        ConnectionFailure.PermissionDenied -> DeviceFailureAction.OpenAppSettings
        ConnectionFailure.BluetoothDisabled,
        ConnectionFailure.PairingFailed,
        -> DeviceFailureAction.OpenBluetoothSettings

        ConnectionFailure.BluetoothUnavailable,
        ConnectionFailure.IncompatibleDevice,
        -> DeviceFailureAction.BackToList

        else -> DeviceFailureAction.Retry
    }
}
