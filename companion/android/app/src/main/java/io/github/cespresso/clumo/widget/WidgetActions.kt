package io.github.cespresso.clumo.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import io.github.cespresso.clumo.MainActivity
import io.github.cespresso.clumo.service.DeviceHubService

private const val TAG = "RunWidgetCommand"

val CommandKey = ActionParameters.Key<String>("clumo_widget_command")

class RunWidgetCommand : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val command = WidgetCommand.decode(parameters[CommandKey]) ?: return
        // A platform that refuses the foreground start throws out of the callback, and a tap
        // on a home screen widget must never crash. The app reaches the same controls.
        runCatching { DeviceHubService.start(context, command) }.onFailure { failure ->
            Log.w(TAG, "Foreground start refused; opening the app instead", failure)
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
