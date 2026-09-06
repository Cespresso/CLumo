package io.github.cespresso.clumo.widget

import android.content.Context
import io.github.cespresso.clumo.R

fun headlineText(context: Context, snapshot: WidgetSnapshot): String =
    context.getString(
        when (snapshot.headline) {
            WidgetHeadline.PomodoroWorking -> R.string.widget_working
            WidgetHeadline.PomodoroBreak -> R.string.widget_break
            WidgetHeadline.PomodoroIdle -> R.string.widget_pomodoro
            WidgetHeadline.Timer -> R.string.widget_timer
            WidgetHeadline.TimerIdle -> R.string.widget_timer
            WidgetHeadline.Paused -> R.string.widget_paused
            WidgetHeadline.MyDisplay -> R.string.widget_my_display
            WidgetHeadline.Visualizer -> R.string.widget_visualizer
            WidgetHeadline.Connecting -> R.string.widget_connecting
            WidgetHeadline.CantConnect -> R.string.widget_cant_connect
            WidgetHeadline.NotConnected -> R.string.widget_not_connected
            WidgetHeadline.ChooseDevice -> R.string.widget_choose_device
            WidgetHeadline.BluetoothOff -> R.string.widget_bluetooth_off
            WidgetHeadline.PermissionNeeded -> R.string.widget_permission_needed
        }
    )

fun subtitleText(context: Context, snapshot: WidgetSnapshot): String = when (snapshot.subtitle) {
    WidgetSubtitle.None -> ""
    WidgetSubtitle.Alias, WidgetSubtitle.PatternName -> snapshot.subtitleText
    WidgetSubtitle.PomodoroDurations -> context.getString(
        R.string.widget_sub_pomodoro_durations, snapshot.subtitleArgA, snapshot.subtitleArgB,
    )
    WidgetSubtitle.TimerDuration -> context.getString(
        R.string.widget_sub_timer_duration, snapshot.subtitleArgA, snapshot.subtitleArgB,
    )
    WidgetSubtitle.ReactingToSound -> context.getString(R.string.widget_sub_reacting)
    WidgetSubtitle.CheckPowerAndBluetooth -> context.getString(R.string.widget_sub_check_power)
    WidgetSubtitle.TapToReconnect -> context.getString(R.string.widget_sub_tap_reconnect)
    WidgetSubtitle.TapToOpenSettings -> context.getString(R.string.widget_sub_tap_settings)
    WidgetSubtitle.TapToOpenApp -> context.getString(R.string.widget_sub_tap_app)
}

fun actionLabel(context: Context, action: WidgetAction): String = context.getString(
    when (action) {
        WidgetAction.Start -> R.string.widget_action_start
        WidgetAction.Pause -> R.string.widget_action_pause
        WidgetAction.Reset -> R.string.widget_action_reset
        WidgetAction.Cancel -> R.string.widget_action_cancel
        WidgetAction.Retry -> R.string.widget_action_retry
    }
)
