package io.github.cespresso.clumo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.cespresso.clumo.MainActivity

private const val FACE_PX = 162
private const val FACE_DP = 54f

/**
 * The device as an object on the home screen. It only reports: the control widget owns
 * operation, and a tap here opens the app in every state.
 */
class ClumoPresenceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetSnapshotStore(context)
        val seed = store.read()
        provideContent {
            PresenceContent(context, rememberWidgetSnapshot(store, seed))
        }
    }

    @Composable
    private fun PresenceContent(context: Context, snapshot: WidgetSnapshot?) {
        val shown = snapshot ?: disconnectedSnapshot()
        val connected = shown.link == WidgetLink.Ready
        val layout = deviceArtLayout(FACE_DP, ringed = true)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetPalette.Panel)
                .cornerRadius(22.dp)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(renderDeviceBitmap(shown, FACE_PX, ringed = true)),
                contentDescription = null,
                modifier = GlanceModifier.size(layout.widthPx.dp, layout.heightPx.dp),
            )
            Spacer(GlanceModifier.height(4.dp))
            // The alias is the only thing telling two CLumos apart on a home screen. Every
            // failing state draws an identical device, so there the caption names the state.
            val caption = shown.alias.takeIf { connected && it.isNotBlank() }
                ?: headlineText(context, shown)
            Text(
                text = caption,
                // The enclosure already claims most of a 2x2 cell, so a long blocked-state
                // caption has to ellipsize rather than push itself out of the widget.
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (connected) WidgetPalette.Text else WidgetPalette.Muted
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class ClumoPresenceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClumoPresenceWidget()
}
