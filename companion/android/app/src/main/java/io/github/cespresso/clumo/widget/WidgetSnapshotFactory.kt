package io.github.cespresso.clumo.widget

import androidx.compose.ui.graphics.toArgb
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.accentContentToneFor
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.domain.backgroundCountdownsFor

/** A condition a widget cannot recover from on its own. */
enum class WidgetBlock {
    BluetoothOff,
    PermissionMissing,
}

/**
 * Builds the whole visible state of both widgets. Every input is a parameter, the clock
 * included, so each state it can produce is unit testable.
 */
object WidgetSnapshotFactory {

    @Suppress("LongParameterList")
    fun create(
        target: Device?,
        block: WidgetBlock?,
        connectionState: ConnectionState,
        mode: Int?,
        pomodoro: PomodoroStatus?,
        timer: CountdownTimerStatus?,
        patternName: String?,
        committedFrame: Long?,
        alias: String,
        appearance: DeviceAppearance,
        commandFailed: Boolean,
        nowRealtime: Long,
    ): WidgetSnapshot {
        val base = identity(alias, appearance, nowRealtime)

        // Neither block can be cleared from a widget, so both outrank the connection state.
        if (block != null) {
            return base.copy(
                link = WidgetLink.Blocked,
                headline = when (block) {
                    WidgetBlock.BluetoothOff -> WidgetHeadline.BluetoothOff
                    WidgetBlock.PermissionMissing -> WidgetHeadline.PermissionNeeded
                },
                subtitle = WidgetSubtitle.TapToOpenApp,
            )
        }

        if (target == null) {
            return base.copy(
                link = WidgetLink.NoTarget,
                headline = WidgetHeadline.ChooseDevice,
                subtitle = WidgetSubtitle.TapToOpenSettings,
                // Nothing is resolved, so there is no device to name.
                alias = "",
                facePlaceholder = true,
            )
        }

        if (connectionState == ConnectionState.Error || commandFailed) {
            return base.copy(
                link = WidgetLink.Failed,
                headline = WidgetHeadline.CantConnect,
                subtitle = WidgetSubtitle.CheckPowerAndBluetooth,
                actions = listOf(WidgetAction.Retry),
            )
        }

        // Disconnected is not an attempt in progress, so it must not read as one, and it is
        // the one non-Ready state a widget can act on.
        if (connectionState == ConnectionState.Disconnected) return base.asDisconnected()

        if (connectionState != ConnectionState.Ready) {
            return base.copy(
                link = WidgetLink.Connecting,
                headline = WidgetHeadline.Connecting,
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
            )
        }

        // An unknown mode falls through to the Pomodoro layout below, so the flag has to be
        // resolved against the mode actually rendered, not the raw one.
        val renderedMode = if (mode in DeviceMode.ORDER) mode else DeviceMode.POMODORO
        val withBackgroundFlag = base.copy(
            backgroundTimerActive =
            backgroundCountdownsFor(renderedMode, pomodoro, timer).isNotEmpty(),
        )

        return when (renderedMode) {
            DeviceMode.TIMER -> timerState(
                withBackgroundFlag.copy(family = WidgetFamily.Timer),
                timer ?: CountdownTimerStatus.DEFAULT,
                alias,
            )
            // The device has no concept of a pattern name; the publisher looks one up.
            DeviceMode.DISPLAY -> withBackgroundFlag.copy(
                headline = WidgetHeadline.MyDisplay,
                subtitle = WidgetSubtitle.PatternName,
                subtitleText = patternName.orEmpty(),
                faceBits = committedFrame ?: FaceBits.EMPTY,
            )
            DeviceMode.VISUALIZER -> withBackgroundFlag.copy(
                headline = WidgetHeadline.Visualizer,
                subtitle = WidgetSubtitle.ReactingToSound,
                faceBits = VISUALIZER_GLYPH,
            )
            else -> pomodoroState(
                withBackgroundFlag.copy(family = WidgetFamily.Pomodoro),
                pomodoro ?: PomodoroStatus.DEFAULT,
                alias,
            )
        }
    }

    /**
     * The part of a snapshot the link has no say in: who the device is and what it looks like.
     * Every state [create] produces is a copy of this, and so is what a widget draws once a
     * snapshot has aged out.
     */
    fun identity(alias: String, appearance: DeviceAppearance, nowRealtime: Long): WidgetSnapshot =
        WidgetSnapshot(
            link = WidgetLink.Ready,
            headline = WidgetHeadline.Connecting,
            subtitle = WidgetSubtitle.None,
            alias = alias,
            enclosureArgb = argb(appearance.enclosureColor.value),
            ctaArgb = argb(appearance.buttonAColor.value),
            onCtaArgb = contentArgb(accentContentToneFor(appearance.buttonAColor)),
            knobArgb = argb(appearance.buttonBColor.value),
            ledArgb = argb(appearance.ledColor.value),
            updatedAtRealtime = nowRealtime,
        )

    private fun pomodoroState(
        base: WidgetSnapshot,
        status: PomodoroStatus,
        alias: String,
    ): WidgetSnapshot =
        when {
            status.isRunning -> base.copy(
                headline = if (status.isWorkPhase) {
                    WidgetHeadline.PomodoroWorking
                } else {
                    WidgetHeadline.PomodoroBreak
                },
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
                faceBits = FaceBits.fromPomodoro(status),
                actions = listOf(WidgetAction.Pause, WidgetAction.Reset),
            )
            status.isIdle -> base.copy(
                headline = WidgetHeadline.PomodoroIdle,
                subtitle = WidgetSubtitle.PomodoroDurations,
                subtitleArgA = status.workMin,
                subtitleArgB = status.breakMin,
                faceBits = -1L,
                actions = listOf(WidgetAction.Start),
            )
            else -> base.copy(
                headline = WidgetHeadline.Paused,
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
                faceBits = FaceBits.fromPomodoro(status),
                faceDimmed = true,
                actions = listOf(WidgetAction.Start, WidgetAction.Reset),
            )
        }

    private fun timerState(
        base: WidgetSnapshot,
        status: CountdownTimerStatus,
        alias: String,
    ): WidgetSnapshot =
        when {
            status.isRunning -> base.copy(
                headline = WidgetHeadline.Timer,
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
                faceBits = FaceBits.fromCountdownTimer(status),
                actions = listOf(WidgetAction.Pause, WidgetAction.Cancel),
            )
            status.isPaused -> base.copy(
                headline = WidgetHeadline.Paused,
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
                faceBits = FaceBits.fromCountdownTimer(status),
                faceDimmed = true,
                actions = listOf(WidgetAction.Start, WidgetAction.Cancel),
            )
            // Idle and Completed both present as "ready to start".
            else -> base.copy(
                headline = WidgetHeadline.TimerIdle,
                subtitle = WidgetSubtitle.TimerDuration,
                subtitleArgA = status.configuredMin,
                subtitleArgB = status.configuredSec,
                faceBits = -1L,
                actions = listOf(WidgetAction.Start),
            )
        }

    /** RgbColor stores opaque sRGB without an alpha channel. */
    private fun argb(rgb: Int): Int = rgb or (0xFF shl 24)

    /** The same two content colors the app draws on an accent fill. */
    private fun contentArgb(tone: ContentTone): Int =
        when (tone) {
            ContentTone.Dark -> ClumoColors.Text.toArgb()
            ContentTone.Light -> ClumoColors.White.toArgb()
        }
}
