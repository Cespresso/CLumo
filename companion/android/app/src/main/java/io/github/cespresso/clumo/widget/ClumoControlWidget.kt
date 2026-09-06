package io.github.cespresso.clumo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.cespresso.clumo.MainActivity

private const val FACE_PX = 156
private const val FACE_DP = 52f

/** The widget that operates the device. A tap outside a button opens the app. */
class ClumoControlWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetSnapshotStore(context)
        val seed = store.read()
        provideContent {
            ControlContent(context, rememberWidgetSnapshot(store, seed))
        }
    }

    @Composable
    private fun ControlContent(context: Context, snapshot: WidgetSnapshot?) {
        val shown = snapshot ?: disconnectedSnapshot()
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetPalette.Panel)
                .cornerRadius(20.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val layout = deviceArtLayout(FACE_DP, ringed = false)
            Image(
                provider = ImageProvider(renderDeviceBitmap(shown, FACE_PX, ringed = false)),
                contentDescription = null,
                modifier = GlanceModifier.size(layout.widthPx.dp, layout.heightPx.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    headlineText(context, shown),
                    style = TextStyle(
                        color = ColorProvider(WidgetPalette.Text),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    subtitleText(context, shown),
                    style = TextStyle(color = ColorProvider(WidgetPalette.Muted)),
                )
            }
            shown.actions.forEach { action ->
                Spacer(GlanceModifier.width(8.dp))
                ActionButton(context, action, shown)
            }
        }
    }

    @Composable
    private fun ActionButton(context: Context, action: WidgetAction, snapshot: WidgetSnapshot) {
        val command = commandFor(action, snapshot) ?: return
        val isCta = action == WidgetAction.Start || action == WidgetAction.Pause
        Text(
            text = actionLabel(context, action),
            style = TextStyle(
                color = ColorProvider(
                    if (isCta) Color(snapshot.onCtaArgb) else WidgetPalette.Muted
                ),
            ),
            modifier = GlanceModifier
                .background(
                    if (isCta) Color(snapshot.ctaArgb) else WidgetPalette.NeutralButton
                )
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    actionRunCallback<RunWidgetCommand>(
                        actionParametersOf(CommandKey to WidgetCommand.encode(command))
                    )
                ),
        )
    }

    private fun commandFor(action: WidgetAction, snapshot: WidgetSnapshot): WidgetCommand? =
        when (action) {
            WidgetAction.Start, WidgetAction.Pause -> when (snapshot.family) {
                WidgetFamily.Timer -> WidgetCommand.ToggleTimer
                WidgetFamily.Pomodoro -> WidgetCommand.TogglePomodoro
                WidgetFamily.Neither -> null
            }
            WidgetAction.Reset -> WidgetCommand.ResetPomodoro
            WidgetAction.Cancel -> WidgetCommand.CancelTimer
            WidgetAction.Retry -> WidgetCommand.Retry
        }
}

class ClumoControlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClumoControlWidget()
}
