package io.github.cespresso.clumo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.cespresso.clumo.MainActivity

private const val FACE_PX = 128

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
        val connected = snapshot != null && snapshot.link == WidgetLink.Ready
        val shown = snapshot ?: disconnectedSnapshot()
        val enclosure =
            if (connected) Color(shown.enclosureArgb) else WidgetPalette.EnclosureOffline
        // Ready draws the ring in the card colour, leaving the enclosure undecorated.
        val ring = when {
            !connected && shown.link == WidgetLink.Failed -> WidgetPalette.RingFailed
            !connected -> WidgetPalette.RingIdle
            else -> WidgetPalette.Panel // card colour: no visible ring when Ready
        }
        val bits = if (connected) shown.faceBits else 0L

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
            // Status decoration has to stay off the device body, so the ring sits outside
            // the enclosure and never tints it.
            Column(
                modifier = GlanceModifier
                    .background(ring)
                    .cornerRadius(19.dp)
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = GlanceModifier
                        .background(enclosure)
                        .cornerRadius(17.dp)
                        .padding(9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        provider = ImageProvider(
                            if (shown.facePlaceholder) {
                                renderPlaceholderBitmap(
                                    FACE_PX, WidgetPalette.PlaceholderStrokeArgb
                                )
                            } else {
                                renderFaceBitmap(
                                    bits = bits,
                                    sizePx = FACE_PX,
                                    litArgb = shown.ledArgb,
                                    offArgb = WidgetPalette.OffDotOnEnclosureArgb,
                                    dimmed = shown.faceDimmed,
                                )
                            }
                        ),
                        contentDescription = null,
                        modifier = GlanceModifier.size(52.dp),
                    )
                    Spacer(GlanceModifier.height(5.dp))
                    Row(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob(Color(shown.ctaArgb), connected)
                        Spacer(GlanceModifier.width(13.dp))
                        Knob(Color(shown.knobArgb), connected)
                    }
                }
            }
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

    @Composable
    private fun Knob(color: Color, connected: Boolean) {
        Box(
            modifier = GlanceModifier
                .size(7.dp)
                .cornerRadius(4.dp)
                .background(if (connected) color else WidgetPalette.KnobOffline),
            content = {},
        )
    }
}

class ClumoPresenceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClumoPresenceWidget()
}
