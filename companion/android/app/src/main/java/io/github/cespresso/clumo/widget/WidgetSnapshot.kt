package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits

/** How usable the link to the primary device is right now. */
enum class WidgetLink {
    Ready,
    Connecting,
    Failed,
    NoTarget,
    Blocked,
}

/** Primary line. Resolved to a localized string at render time. */
enum class WidgetHeadline {
    PomodoroWorking,
    PomodoroBreak,
    PomodoroIdle,
    Timer,
    TimerIdle,
    Paused,
    MyDisplay,
    Visualizer,
    Connecting,
    CantConnect,
    NotConnected,
    ChooseDevice,
    BluetoothOff,
    PermissionNeeded,
}

/**
 * Secondary line. [WidgetSnapshot.subtitleText] carries text that is not localizable
 * (a device alias, a pattern name); [WidgetSnapshot.subtitleArgA] and
 * [WidgetSnapshot.subtitleArgB] carry numeric arguments for the duration variants.
 */
enum class WidgetSubtitle {
    None,
    Alias,
    PatternName,
    PomodoroDurations,
    TimerDuration,
    ReactingToSound,
    CheckPowerAndBluetooth,
    TapToReconnect,
    TapToOpenSettings,
    TapToOpenApp,
}

enum class WidgetAction {
    Start,
    Pause,
    Reset,
    Cancel,
    Retry,
}

/** Which timed mode this snapshot describes. A Start button maps to a different command in each. */
enum class WidgetFamily {
    Pomodoro,
    Timer,
    Neither,
}

/** A fixed equalizer figure. Visualizer frames are never mirrored. */
val VISUALIZER_GLYPH: Long = FaceBits.fromBitsString(
    "00000000" +
        "00010000" +
        "00010100" +
        "01010101" +
        "01011101" +
        "01111101" +
        "11111111" +
        "11111111",
)

/** A snapshot older than this is treated as disconnected. */
const val STALE_THRESHOLD_MS: Long = 25L * 60L * 1000L

/** How often a live service rewrites the timestamp so an idle device stays fresh. */
const val HEARTBEAT_INTERVAL_MS: Long = 10L * 60L * 1000L

/** Everything a widget draws, and nothing else. */
data class WidgetSnapshot(
    val link: WidgetLink,
    val headline: WidgetHeadline,
    val subtitle: WidgetSubtitle,
    val subtitleText: String = "",
    val subtitleArgA: Int = 0,
    val subtitleArgB: Int = 0,
    /**
     * The device's alias. [subtitleText] is polymorphic: a pattern name in display mode,
     * empty whenever the subtitle is generated. A widget that needs the alias reads it here.
     */
    val alias: String = "",
    val faceBits: Long = FaceBits.EMPTY,
    val faceDimmed: Boolean = false,
    val facePlaceholder: Boolean = false,
    /** Covers a Pomodoro as well as a Timer. */
    val backgroundTimerActive: Boolean = false,
    val family: WidgetFamily = WidgetFamily.Neither,
    val actions: List<WidgetAction> = emptyList(),
    val enclosureArgb: Int,
    val ctaArgb: Int,
    /**
     * Text drawn on a [ctaArgb] fill. Button A can be painted any color the appearance
     * editor allows, including ones too light to carry white.
     */
    val onCtaArgb: Int,
    val knobArgb: Int,
    val ledArgb: Int,
    val updatedAtRealtime: Long,
)

/** Whether anything visible differs. [WidgetSnapshot.updatedAtRealtime] alone is not visible. */
fun WidgetSnapshot.sameContentAs(other: WidgetSnapshot): Boolean = copy(updatedAtRealtime = 0L) == other.copy(updatedAtRealtime = 0L)

/**
 * `elapsedRealtime` resets on reboot, so a timestamp in the future is itself proof that the
 * snapshot predates a restart.
 */
fun WidgetSnapshot.isStale(nowRealtime: Long): Boolean =
    nowRealtime < updatedAtRealtime ||
        nowRealtime - updatedAtRealtime > STALE_THRESHOLD_MS

/**
 * This snapshot's device with its link gone. Identity — the alias and the appearance colors —
 * stays, since preferences do not expire; everything the link was reporting is dropped. The
 * factory and the staleness path both end here, so a widget draws the same thing whether the
 * publisher said "disconnected" or simply went quiet.
 */
fun WidgetSnapshot.asDisconnected(): WidgetSnapshot =
    copy(
        link = WidgetLink.Connecting,
        headline = WidgetHeadline.NotConnected,
        subtitle = WidgetSubtitle.TapToReconnect,
        subtitleText = "",
        subtitleArgA = 0,
        subtitleArgB = 0,
        faceBits = FaceBits.EMPTY,
        faceDimmed = false,
        facePlaceholder = false,
        backgroundTimerActive = false,
        family = WidgetFamily.Neither,
        actions = listOf(WidgetAction.Retry),
    )

/** Whether going stale would change what a widget draws. False once the link is already shown gone. */
fun WidgetSnapshot.agesVisibly(): Boolean = !sameContentAs(asDisconnected())

/** What a widget draws before anything has ever been published: no device to name, default colors. */
fun disconnectedSnapshot(): WidgetSnapshot =
    WidgetSnapshotFactory
        .identity(alias = "", appearance = DeviceAppearance.DEFAULT, nowRealtime = 0L)
        .asDisconnected()
