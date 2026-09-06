package io.github.cespresso.clumo.ui.device

import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.PomodoroStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceUiStateTest {

    private fun device(id: String = "abcd1234", name: String? = "CLumo-Desk") = Device(id = id, address = "AA:BB", name = name, lastSeenAt = 0L)

    private fun create(
        connected: Boolean = true,
        state: ConnectionState = ConnectionState.Ready,
        failure: ConnectionFailure? = null,
        reconnectAttempt: Int = 0,
        currentMode: Int? = DeviceMode.POMODORO,
        pendingMode: Int? = null,
        pomodoro: PomodoroStatus? = null,
        timer: CountdownTimerStatus? = null,
        deviceId: String? = "abcd1234",
        scannedName: String? = null,
        knownDevice: Device? = null,
        aliases: Map<String, String> = emptyMap(),
        appearances: Map<String, DeviceAppearance> = emptyMap(),
        primaryDeviceId: String? = null,
        committedFrame: Long? = null,
        library: List<Pattern> = emptyList(),
        brightnessUi: Float = 100f,
        columns: IntArray = IntArray(8),
        visualizerActive: Boolean = false,
        timerBlinkOn: Boolean = true,
        dismissedDialogFailure: ConnectionFailure? = null,
    ) = DeviceUiStateFactory.create(
        connected = connected,
        state = state,
        failure = failure,
        reconnectAttempt = reconnectAttempt,
        currentMode = currentMode,
        pendingMode = pendingMode,
        pomodoro = pomodoro,
        timer = timer,
        deviceId = deviceId,
        scannedName = scannedName,
        knownDevice = knownDevice,
        aliases = aliases,
        appearances = appearances,
        primaryDeviceId = primaryDeviceId,
        committedFrame = committedFrame,
        library = library,
        brightnessUi = brightnessUi,
        columns = columns,
        visualizerActive = visualizerActive,
        timerBlinkOn = timerBlinkOn,
        dismissedDialogFailure = dismissedDialogFailure,
    )

    // --- identity ---

    @Test
    fun anAliasOutranksEveryOtherName() {
        val s = create(
            deviceId = "abcd1234",
            scannedName = "CLumo-Scan",
            knownDevice = device(),
            aliases = mapOf("abcd1234" to "Desk Buddy"),
        )
        assertEquals("Desk Buddy", s.displayName)
    }

    @Test
    fun theScannedNameIsUsedWhenNoAliasIsStored() {
        assertEquals(
            "CLumo-Scan",
            create(scannedName = "CLumo-Scan", knownDevice = device()).displayName,
        )
    }

    @Test
    fun aKnownDeviceNamesItselfWhenNothingIsAdvertising() {
        assertEquals("CLumo-Desk", create(scannedName = null, knownDevice = device()).displayName)
    }

    @Test
    fun anUnknownDeviceFallsBackToTheProductName() {
        assertEquals(
            "CLumo",
            create(deviceId = null, scannedName = null, knownDevice = null).displayName,
        )
    }

    @Test
    fun theFirmwareIdOutranksTheStoredOneForIdentity() {
        val s = create(deviceId = "fromlink", knownDevice = device(id = "fromdisk"))
        assertEquals("fromlink", s.stableId)
    }

    @Test
    fun aDeviceIsPrimaryOnlyWhenItsStableIdMatches() {
        assertEquals(true, create(deviceId = "abcd1234", primaryDeviceId = "abcd1234").isPrimary)
        assertEquals(false, create(deviceId = "abcd1234", primaryDeviceId = "other").isPrimary)
        // No identity yet means nothing to compare, so it cannot be the primary.
        assertEquals(false, create(deviceId = null, knownDevice = null, primaryDeviceId = null).isPrimary)
    }

    // --- mode ---

    @Test
    fun anUnconfirmedTapWinsOverTheModeTheDeviceLastReported() {
        val s = create(currentMode = DeviceMode.POMODORO, pendingMode = DeviceMode.TIMER)
        assertEquals(DeviceMode.TIMER, s.effectiveMode)
    }

    @Test
    fun theDeviceModeIsUsedOnceNothingIsPending() {
        assertEquals(
            DeviceMode.VISUALIZER,
            create(currentMode = DeviceMode.VISUALIZER, pendingMode = null).effectiveMode,
        )
    }

    @Test
    fun pomodoroIsTheModeShownBeforeTheDeviceHasAnswered() {
        assertEquals(
            DeviceMode.POMODORO,
            create(currentMode = null, pendingMode = null).effectiveMode,
        )
    }

    // --- connection labels ---

    @Test
    fun everyConnectionStateHasItsOwnLabel() {
        val labels = ConnectionState.entries.map { create(state = it).stateLabel }
        assertEquals(ConnectionState.entries.size, labels.toSet().size)
    }

    @Test
    fun onlyReadyReadsAsAccentAndOnlyErrorReadsAsError() {
        assertEquals(DeviceStateTone.Accent, create(state = ConnectionState.Ready).stateTone)
        assertEquals(DeviceStateTone.Error, create(state = ConnectionState.Error).stateTone)
        assertEquals(DeviceStateTone.Muted, create(state = ConnectionState.Connecting).stateTone)
        assertEquals(DeviceStateTone.Muted, create(state = ConnectionState.Disconnected).stateTone)
    }

    @Test
    fun theReconnectLabelCarriesTheAttemptCount() {
        val s = create(state = ConnectionState.Reconnecting, reconnectAttempt = 3)
        assertEquals(DeviceStateLabel.Reconnecting, s.stateLabel)
        assertEquals(3, s.reconnectAttempt)
    }

    @Test
    fun controlsAreLiveOnlyWhenTheLinkIsReady() {
        assertEquals(true, create(state = ConnectionState.Ready).ready)
        ConnectionState.entries.filter { it != ConnectionState.Ready }.forEach {
            assertEquals(false, create(state = it).ready)
        }
    }

    // --- failures ---

    @Test
    fun eachFailureReasonHasItsOwnMessage() {
        val messages = ConnectionFailure.entries.map { create(failure = it).failureMessage }
        assertEquals(ConnectionFailure.entries.size, messages.toSet().size)
    }

    @Test
    fun aLostLinkAndAnUnreportedOneReadTheSame() {
        assertEquals(
            create(failure = ConnectionFailure.ConnectionLost).failureMessage,
            create(failure = null).failureMessage,
        )
    }

    @Test
    fun theBannerOffersSettingsOnlyForTheTwoFailuresSettingsCanFix() {
        assertEquals(
            DeviceFailureAction.OpenAppSettings,
            create(failure = ConnectionFailure.PermissionDenied).bannerAction,
        )
        assertEquals(
            DeviceFailureAction.OpenBluetoothSettings,
            create(failure = ConnectionFailure.BluetoothDisabled).bannerAction,
        )
        assertEquals(
            DeviceFailureAction.Retry,
            create(failure = ConnectionFailure.ConnectionTimedOut).bannerAction,
        )
    }

    // --- the blocking dialog ---

    @Test
    fun onlyFailuresTheScreenCannotRetryPastRaiseADialog() {
        val blocking = listOf(
            ConnectionFailure.BluetoothUnavailable,
            ConnectionFailure.PermissionDenied,
            ConnectionFailure.BluetoothDisabled,
            ConnectionFailure.PairingFailed,
            ConnectionFailure.IncompatibleDevice,
        )
        ConnectionFailure.entries.forEach { failure ->
            val dialog = create(state = ConnectionState.Error, failure = failure).dialog
            if (failure in blocking) {
                assertEquals(failure, dialog?.failure)
            } else {
                assertNull("$failure should not block", dialog)
            }
        }
    }

    @Test
    fun aDialogNeedsTheLinkToActuallyBeInError() {
        assertNull(
            create(state = ConnectionState.Connecting, failure = ConnectionFailure.PairingFailed).dialog,
        )
    }

    @Test
    fun dismissingADialogKeepsItDownUntilTheFailureChanges() {
        assertNull(
            create(
                state = ConnectionState.Error,
                failure = ConnectionFailure.PairingFailed,
                dismissedDialogFailure = ConnectionFailure.PairingFailed,
            ).dialog,
        )
        assertEquals(
            ConnectionFailure.BluetoothDisabled,
            create(
                state = ConnectionState.Error,
                failure = ConnectionFailure.BluetoothDisabled,
                dismissedDialogFailure = ConnectionFailure.PairingFailed,
            ).dialog?.failure,
        )
    }

    @Test
    fun aFailedPairingSendsTheUserToBluetoothSettings() {
        // The button says "open settings" like a permission failure does, but pairing is
        // repaired in the Bluetooth screen, not in the app's own settings page.
        val dialog = create(
            state = ConnectionState.Error,
            failure = ConnectionFailure.PairingFailed,
        ).dialog
        assertEquals(DeviceFailureAction.OpenBluetoothSettings, dialog?.confirm)
    }

    @Test
    fun aDeviceTheAppCannotUseSendsTheUserBack() {
        listOf(ConnectionFailure.BluetoothUnavailable, ConnectionFailure.IncompatibleDevice)
            .forEach {
                assertEquals(
                    DeviceFailureAction.BackToList,
                    create(state = ConnectionState.Error, failure = it).dialog?.confirm,
                )
            }
    }

    // --- brightness ---

    @Test
    fun theFaceNeverDimsBelowFortyPercentOfItsLitAlpha() {
        assertEquals(0.4f, create(brightnessUi = 0f).litAlpha, 0.0001f)
        assertEquals(1.0f, create(brightnessUi = 100f).litAlpha, 0.0001f)
        assertEquals(0.7f, create(brightnessUi = 50f).litAlpha, 0.0001f)
    }

    // --- the live LED mirror ---

    @Test
    fun aLinkThatIsNotReadyMirrorsNothing() {
        assertEquals(
            FaceBits.EMPTY,
            create(state = ConnectionState.Connecting, currentMode = DeviceMode.DISPLAY, committedFrame = -1L).mirrorBits,
        )
    }

    @Test
    fun pomodoroMirrorsItsPixelCountdown() {
        val status = PomodoroStatus(PomodoroStatus.STATE_RUNNING, PomodoroStatus.PHASE_WORK, 300, 10, 5)
        assertEquals(
            FaceBits.fromPomodoro(status),
            create(currentMode = DeviceMode.POMODORO, pomodoro = status).mirrorBits,
        )
    }

    @Test
    fun displayModeMirrorsTheFrameTheDeviceReports() {
        val frame = FaceBits.fromBitsString("1".repeat(8) + "0".repeat(56))
        assertEquals(frame, create(currentMode = DeviceMode.DISPLAY, committedFrame = frame).mirrorBits)
    }

    @Test
    fun displayModeWithoutAFrameYetMirrorsNothing() {
        assertEquals(FaceBits.EMPTY, create(currentMode = DeviceMode.DISPLAY, committedFrame = null).mirrorBits)
    }

    @Test
    fun aCompletedTimerBlinksTheWholeFaceOnAndOff() {
        val done = CountdownTimerStatus(CountdownTimerStatus.STATE_COMPLETED, 0, 5, 0)
        assertEquals(
            -1L,
            create(currentMode = DeviceMode.TIMER, timer = done, timerBlinkOn = true).mirrorBits,
        )
        assertEquals(
            FaceBits.EMPTY,
            create(currentMode = DeviceMode.TIMER, timer = done, timerBlinkOn = false).mirrorBits,
        )
    }

    @Test
    fun theMirrorFollowsTheDeviceWhileTheSelectorFollowsTheTap() {
        val running = PomodoroStatus(PomodoroStatus.STATE_RUNNING, PomodoroStatus.PHASE_WORK, 300, 10, 5)
        val s = create(
            currentMode = DeviceMode.POMODORO,
            pendingMode = DeviceMode.DISPLAY,
            pomodoro = running,
            committedFrame = -1L,
        )
        // The segment moves at once so the tap feels answered...
        assertEquals(DeviceMode.DISPLAY, s.effectiveMode)
        // ...but the face still reports the pomodoro the LED is actually showing.
        assertEquals(FaceBits.fromPomodoro(running), s.mirrorBits)
    }

    @Test
    fun theVisualizerMirrorsNothingUntilItIsActuallyHearingSomething() {
        val columns = intArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        assertEquals(
            FaceBits.EMPTY,
            create(currentMode = DeviceMode.VISUALIZER, columns = columns, visualizerActive = false).mirrorBits,
        )
        assertEquals(
            FaceBits.fromColumns(columns),
            create(currentMode = DeviceMode.VISUALIZER, columns = columns, visualizerActive = true).mirrorBits,
        )
    }

    // --- the name beside the mirror ---

    @Test
    fun theShownPatternIsNamedOnlyOverAReadyLink() {
        val heart = Pattern("heart", "Heart", "1".repeat(8) + "0".repeat(56))
        val frame = FaceBits.fromBitsString(heart.bits)

        assertEquals(
            "heart",
            create(currentMode = DeviceMode.DISPLAY, committedFrame = frame, library = listOf(heart)).shownPatternId,
        )
        // Same frame still known to the session, but the link is on its way back.
        assertNull(
            create(
                state = ConnectionState.Reconnecting,
                currentMode = DeviceMode.DISPLAY,
                committedFrame = frame,
                library = listOf(heart),
            ).shownPatternId,
        )
    }

    @Test
    fun aFrameTheLibraryLacksNamesNothing() {
        val heart = Pattern("heart", "Heart", "1".repeat(8) + "0".repeat(56))
        assertNull(create(currentMode = DeviceMode.DISPLAY, committedFrame = -1L, library = listOf(heart)).shownPatternId)
    }

    @Test
    fun aScreenWithNoConnectionShowsTheDisconnectedShell() {
        val s = create(connected = false, state = ConnectionState.Disconnected, deviceId = null, knownDevice = device())
        assertEquals(false, s.ready)
        assertEquals(FaceBits.EMPTY, s.mirrorBits)
        assertEquals(DeviceStateLabel.Disconnected, s.stateLabel)
        assertEquals("CLumo-Desk", s.displayName)
    }
}
