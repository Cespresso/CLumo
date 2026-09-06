package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotFactoryTest {

    private val target = Device("id-a", "AA:AA:AA:AA:AA:AA", "CLumo-AAAA", 0L)

    private fun create(
        target: Device? = this.target,
        block: WidgetBlock? = null,
        connectionState: ConnectionState = ConnectionState.Ready,
        mode: Int? = DeviceMode.POMODORO,
        pomodoro: PomodoroStatus? = PomodoroStatus.DEFAULT,
        timer: CountdownTimerStatus? = CountdownTimerStatus.DEFAULT,
        patternName: String? = "ハート",
        patternBits: Long = FaceBits.EMPTY,
        committedFrame: Long? = null,
        alias: String = "つくえ",
        appearance: DeviceAppearance = DeviceAppearance.DEFAULT,
        commandFailed: Boolean = false,
    ) = WidgetSnapshotFactory.create(
        target = target,
        block = block,
        connectionState = connectionState,
        mode = mode,
        pomodoro = pomodoro,
        timer = timer,
        patternName = patternName,
        patternBits = patternBits,
        committedFrame = committedFrame,
        alias = alias,
        appearance = appearance,
        commandFailed = commandFailed,
        nowRealtime = 1_000L,
    )

    private val running = PomodoroStatus(
        PomodoroStatus.STATE_RUNNING,
        PomodoroStatus.PHASE_WORK,
        15 * 60,
        25,
        5,
    )

    @Test
    fun state1_pomodoroRunning() {
        val s = create(pomodoro = running)
        assertEquals(WidgetLink.Ready, s.link)
        assertEquals(WidgetHeadline.PomodoroWorking, s.headline)
        assertEquals(WidgetSubtitle.Alias, s.subtitle)
        assertEquals("つくえ", s.subtitleText)
        assertEquals(FaceBits.fromPomodoro(running), s.faceBits)
        assertFalse(s.faceDimmed)
        assertEquals(WidgetFamily.Pomodoro, s.family)
        assertEquals(listOf(WidgetAction.Pause, WidgetAction.Reset), s.actions)
    }

    @Test
    fun familyDistinguishesPomodoroFromTimerWithoutInspectingButtons() {
        assertEquals(WidgetFamily.Pomodoro, create().family)
        assertEquals(WidgetFamily.Timer, create(mode = DeviceMode.TIMER).family)
        assertEquals(WidgetFamily.Neither, create(mode = DeviceMode.DISPLAY).family)
        assertEquals(WidgetFamily.Neither, create(mode = DeviceMode.VISUALIZER).family)
    }

    @Test
    fun state1_breakPhaseChangesOnlyTheHeadline() {
        val onBreak = running.copy(phase = PomodoroStatus.PHASE_BREAK)
        assertEquals(WidgetHeadline.PomodoroBreak, create(pomodoro = onBreak).headline)
    }

    @Test
    fun state2_pomodoroPausedDimsTheFace() {
        val paused = running.copy(state = PomodoroStatus.STATE_PAUSED)
        val s = create(pomodoro = paused)
        assertEquals(WidgetHeadline.Paused, s.headline)
        assertTrue(s.faceDimmed)
        assertEquals(FaceBits.fromPomodoro(paused), s.faceBits)
        assertEquals(listOf(WidgetAction.Start, WidgetAction.Reset), s.actions)
    }

    @Test
    fun state3_pomodoroIdleShowsDurationsAndOneButton() {
        val s = create(pomodoro = PomodoroStatus.DEFAULT)
        assertEquals(WidgetHeadline.PomodoroIdle, s.headline)
        assertEquals(WidgetSubtitle.PomodoroDurations, s.subtitle)
        assertEquals(25, s.subtitleArgA)
        assertEquals(5, s.subtitleArgB)
        assertEquals(-1L, s.faceBits)
        assertEquals(listOf(WidgetAction.Start), s.actions)
    }

    @Test
    fun state4_timerRunningOffersCancelNotReset() {
        val t = CountdownTimerStatus(CountdownTimerStatus.STATE_RUNNING, 120, 5, 0)
        val s = create(mode = DeviceMode.TIMER, timer = t)
        assertEquals(WidgetHeadline.Timer, s.headline)
        assertEquals(WidgetFamily.Timer, s.family)
        assertEquals(FaceBits.fromCountdownTimer(t), s.faceBits)
        assertEquals(listOf(WidgetAction.Pause, WidgetAction.Cancel), s.actions)
    }

    @Test
    fun state5_timerPausedDimsTheFace() {
        val t = CountdownTimerStatus(CountdownTimerStatus.STATE_PAUSED, 120, 5, 0)
        val s = create(mode = DeviceMode.TIMER, timer = t)
        assertEquals(WidgetHeadline.Paused, s.headline)
        assertTrue(s.faceDimmed)
        assertEquals(listOf(WidgetAction.Start, WidgetAction.Cancel), s.actions)
    }

    @Test
    fun state6_timerIdleShowsConfiguredDuration() {
        val t = CountdownTimerStatus(CountdownTimerStatus.STATE_IDLE, 90, 1, 30)
        val s = create(mode = DeviceMode.TIMER, timer = t)
        assertEquals(WidgetHeadline.TimerIdle, s.headline)
        assertEquals(WidgetSubtitle.TimerDuration, s.subtitle)
        assertEquals(1, s.subtitleArgA)
        assertEquals(30, s.subtitleArgB)
        assertEquals(-1L, s.faceBits)
        assertEquals(listOf(WidgetAction.Start), s.actions)
    }

    @Test
    fun state6_timerCompletedShowsSameAsIdle() {
        val t = CountdownTimerStatus(CountdownTimerStatus.STATE_COMPLETED, 0, 1, 30)
        val s = create(mode = DeviceMode.TIMER, timer = t)
        assertEquals(WidgetHeadline.TimerIdle, s.headline)
        assertEquals(WidgetSubtitle.TimerDuration, s.subtitle)
        assertEquals(1, s.subtitleArgA)
        assertEquals(30, s.subtitleArgB)
        assertEquals(-1L, s.faceBits)
        assertFalse(s.faceDimmed)
        assertEquals(listOf(WidgetAction.Start), s.actions)
    }

    @Test
    fun timerNullFallsBackToDefaultAndShowsIdle() {
        val s = create(mode = DeviceMode.TIMER, timer = null)
        assertEquals(WidgetHeadline.TimerIdle, s.headline)
        assertEquals(WidgetSubtitle.TimerDuration, s.subtitle)
        assertEquals(5, s.subtitleArgA)
        assertEquals(0, s.subtitleArgB)
        assertEquals(-1L, s.faceBits)
        assertFalse(s.faceDimmed)
        assertEquals(listOf(WidgetAction.Start), s.actions)
    }

    @Test
    fun state7_displayModeMirrorsThePatternAndOffersNoButtons() {
        val bits = FaceBits.fromBitsString("1".repeat(8) + "0".repeat(56))
        val s = create(mode = DeviceMode.DISPLAY, patternBits = bits)
        assertEquals(WidgetHeadline.MyDisplay, s.headline)
        assertEquals(WidgetSubtitle.PatternName, s.subtitle)
        assertEquals("ハート", s.subtitleText)
        assertEquals(bits, s.faceBits)
        assertTrue(s.actions.isEmpty())
    }

    @Test
    fun state8_visualizerUsesTheFixedGlyph() {
        val s = create(mode = DeviceMode.VISUALIZER)
        assertEquals(WidgetHeadline.Visualizer, s.headline)
        assertEquals(VISUALIZER_GLYPH, s.faceBits)
        assertTrue(s.actions.isEmpty())
    }

    @Test
    fun state9_inFlightConnectionStatesShowConnecting() {
        listOf(
            ConnectionState.Connecting,
            ConnectionState.Reconnecting,
            ConnectionState.Bonding,
            ConnectionState.Connected,
            ConnectionState.Synchronizing,
        ).forEach { state ->
            val s = create(connectionState = state)
            assertEquals(WidgetLink.Connecting, s.link)
            assertEquals(WidgetHeadline.Connecting, s.headline)
            assertEquals(FaceBits.EMPTY, s.faceBits)
            assertTrue(s.actions.isEmpty())
        }
    }

    @Test
    fun disconnectedDoesNotClaimToBeConnecting() {
        val s = create(connectionState = ConnectionState.Disconnected)
        assertEquals(WidgetHeadline.NotConnected, s.headline)
        assertEquals(WidgetSubtitle.TapToReconnect, s.subtitle)
        assertEquals(listOf(WidgetAction.Retry), s.actions)
    }

    @Test
    fun state10_errorOffersRetry() {
        val s = create(connectionState = ConnectionState.Error)
        assertEquals(WidgetLink.Failed, s.link)
        assertEquals(WidgetHeadline.CantConnect, s.headline)
        assertEquals(WidgetSubtitle.CheckPowerAndBluetooth, s.subtitle)
        assertEquals(listOf(WidgetAction.Retry), s.actions)
    }

    @Test
    fun state10_aTimedOutCommandAlsoCountsAsFailure() {
        val s = create(connectionState = ConnectionState.Disconnected, commandFailed = true)
        assertEquals(WidgetLink.Failed, s.link)
        assertEquals(listOf(WidgetAction.Retry), s.actions)
    }

    @Test
    fun state12_noTargetAsksForOne() {
        val s = create(target = null)
        assertEquals(WidgetLink.NoTarget, s.link)
        assertEquals(WidgetHeadline.ChooseDevice, s.headline)
        assertEquals(WidgetSubtitle.TapToOpenSettings, s.subtitle)
        assertTrue(s.facePlaceholder)
        assertTrue(s.actions.isEmpty())
    }

    @Test
    fun state13_blockedOutranksEverythingElse() {
        val off = create(block = WidgetBlock.BluetoothOff, connectionState = ConnectionState.Ready)
        assertEquals(WidgetLink.Blocked, off.link)
        assertEquals(WidgetHeadline.BluetoothOff, off.headline)
        assertTrue(off.actions.isEmpty())

        val denied = create(block = WidgetBlock.PermissionMissing)
        assertEquals(WidgetHeadline.PermissionNeeded, denied.headline)
    }

    @Test
    fun appearanceColorsArePassedThrough() {
        val s = create()
        assertEquals(0xFF7E9E7C.toInt(), s.enclosureArgb)
        assertEquals(0xFFE8907E.toInt(), s.ctaArgb)
        assertEquals(0xFFFFFFFF.toInt(), s.knobArgb)
        assertEquals(0xFFF0A35E.toInt(), s.ledArgb)
    }

    @Test
    fun ctaLabelToneFollowsTheButtonColor() {
        // The default coral is dark enough for a white label.
        assertEquals(0xFFFFFFFF.toInt(), create().onCtaArgb)
        // Cream is not, and the appearance editor allows it. Assuming white here is what
        // put pale-on-pale labels on the widget.
        val cream = DeviceAppearance.DEFAULT.copy(
            buttonAColor = requireNotNull(RgbColor.parseOrNull("#F6EFD9")),
        )
        assertEquals(0xFF3E3A36.toInt(), create(appearance = cream).onCtaArgb)
    }

    @Test
    fun everyStateCarriesTheAliasBesideWhateverTheSubtitleHolds() {
        // The subtitle is polymorphic; the alias is not, which is what lets the presence
        // widget name the device in states where the subtitle is something else entirely.
        assertEquals("つくえ", create(mode = DeviceMode.DISPLAY).alias)
        assertEquals("ハート", create(mode = DeviceMode.DISPLAY).subtitleText)
        assertEquals("つくえ", create(mode = DeviceMode.VISUALIZER).alias)
        assertEquals("つくえ", create(pomodoro = PomodoroStatus.DEFAULT).alias)
        assertEquals("つくえ", create(pomodoro = running).alias)
        assertEquals("つくえ", create(mode = DeviceMode.TIMER).alias)
        assertEquals("つくえ", create(connectionState = ConnectionState.Connecting).alias)
        assertEquals("つくえ", create(connectionState = ConnectionState.Error).alias)
        assertEquals("つくえ", create(connectionState = ConnectionState.Disconnected).alias)
    }

    @Test
    fun noTargetCarriesNoAlias() {
        assertEquals("", create(target = null).alias)
    }

    @Test
    fun missingStatusFallsBackToIdleRatherThanCrashing() {
        val s = create(pomodoro = null)
        assertEquals(WidgetHeadline.PomodoroIdle, s.headline)
    }

    @Test
    fun displayFaceFollowsTheDeviceConfirmedFrameSoTheWidgetMatchesTheAppScreen() {
        val selected = FaceBits.fromBitsString("1".repeat(64))
        val confirmed = FaceBits.fromBitsString("1".repeat(8) + "0".repeat(56))
        assertEquals(
            confirmed,
            create(
                mode = DeviceMode.DISPLAY,
                patternBits = selected,
                committedFrame = confirmed,
            ).faceBits,
        )
        // Firmware that cannot report one leaves the local selection as the only source.
        assertEquals(
            selected,
            create(mode = DeviceMode.DISPLAY, patternBits = selected, committedFrame = null)
                .faceBits,
        )
    }

    @Test
    fun anUnknownModeRendersPomodoroWithoutAlsoCallingItBackgrounded() {
        // mode is null until the first snapshot lands; the `else` arm draws Pomodoro, so
        // flagging it as running elsewhere would badge the very thing being shown.
        val s = create(mode = null, pomodoro = running)
        assertEquals(WidgetHeadline.PomodoroWorking, s.headline)
        assertFalse(s.backgroundTimerActive)
    }

    @Test
    fun backgroundTimerActiveFlagsAPomodoroOrTimerRunningInAnotherMode() {
        assertTrue(create(mode = DeviceMode.DISPLAY, pomodoro = running).backgroundTimerActive)
        assertFalse(create(mode = DeviceMode.POMODORO, pomodoro = running).backgroundTimerActive)
        assertFalse(create(mode = DeviceMode.DISPLAY).backgroundTimerActive)

        val runningTimer = CountdownTimerStatus(CountdownTimerStatus.STATE_RUNNING, 30, 1, 0)
        assertTrue(create(mode = DeviceMode.VISUALIZER, timer = runningTimer).backgroundTimerActive)
        assertFalse(create(mode = DeviceMode.TIMER, timer = runningTimer).backgroundTimerActive)
    }
}
