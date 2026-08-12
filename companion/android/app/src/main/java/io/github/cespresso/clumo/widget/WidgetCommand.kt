package io.github.cespresso.clumo.widget

/** An action a widget asks the service to perform, encoded as a string to ride an Intent. */
sealed interface WidgetCommand {
    data object TogglePomodoro : WidgetCommand
    data object ResetPomodoro : WidgetCommand
    data object ToggleTimer : WidgetCommand
    data object CancelTimer : WidgetCommand
    data object Retry : WidgetCommand

    companion object {
        const val ACTION = "io.github.cespresso.clumo.action.WIDGET_COMMAND"
        const val EXTRA = "io.github.cespresso.clumo.extra.WIDGET_COMMAND"

        fun encode(command: WidgetCommand): String = when (command) {
            TogglePomodoro -> "toggle_pomodoro"
            ResetPomodoro -> "reset_pomodoro"
            ToggleTimer -> "toggle_timer"
            CancelTimer -> "cancel_timer"
            Retry -> "retry"
        }

        fun decode(raw: String?): WidgetCommand? = when (raw) {
            "toggle_pomodoro" -> TogglePomodoro
            "reset_pomodoro" -> ResetPomodoro
            "toggle_timer" -> ToggleTimer
            "cancel_timer" -> CancelTimer
            "retry" -> Retry
            else -> null
        }
    }
}
