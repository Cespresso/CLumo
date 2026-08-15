package io.github.cespresso.clumo.widget

import androidx.compose.ui.graphics.toArgb
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.accentContentToneFor
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.PomodoroStatus

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
        patternBits: Long,
        alias: String,
        appearance: DeviceAppearance,
        commandFailed: Boolean,
        nowRealtime: Long,
    ): WidgetSnapshot {
        val base = WidgetSnapshot(
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
        if (connectionState == ConnectionState.Disconnected) {
            return base.copy(
                link = WidgetLink.Connecting,
                headline = WidgetHeadline.NotConnected,
                subtitle = WidgetSubtitle.TapToReconnect,
                actions = listOf(WidgetAction.Retry),
            )
        }

        if (connectionState != ConnectionState.Ready) {
            return base.copy(
                link = WidgetLink.Connecting,
                headline = WidgetHeadline.Connecting,
                subtitle = WidgetSubtitle.Alias,
                subtitleText = alias,
            )
        }

        return when (mode) {
            BleUuids.MODE_TIMER -> timerState(
                base.copy(family = WidgetFamily.Timer),
                timer ?: CountdownTimerStatus.DEFAULT,
                alias,
            )
            BleUuids.MODE_DISPLAY -> base.copy(
                headline = WidgetHeadline.MyDisplay,
                subtitle = WidgetSubtitle.PatternName,
                subtitleText = patternName.orEmpty(),
                faceBits = patternBits,
            )
            BleUuids.MODE_VISUALIZER -> base.copy(
                headline = WidgetHeadline.Visualizer,
                subtitle = WidgetSubtitle.ReactingToSound,
                faceBits = VISUALIZER_GLYPH,
            )
            else -> pomodoroState(
                base.copy(family = WidgetFamily.Pomodoro),
                pomodoro ?: PomodoroStatus.DEFAULT,
                alias,
            )
        }
    }

    private fun pomodoroState(
        base: WidgetSnapshot,
        status: PomodoroStatus,
        alias: String,
    ): WidgetSnapshot = when {
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
    ): WidgetSnapshot = when {
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
    private fun contentArgb(tone: ContentTone): Int = when (tone) {
        ContentTone.Dark -> ClumoColors.Text.toArgb()
        ContentTone.Light -> ClumoColors.White.toArgb()
    }
}
