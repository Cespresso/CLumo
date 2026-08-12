package io.github.cespresso.clumo.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.cespresso.clumo.ui.theme.ClumoColors

/**
 * Fixed widget colors, taken from the app's palette wherever one already exists. The colors
 * a device configures, meaning its enclosure, buttons and LEDs, come from the snapshot and
 * never from here.
 */
internal object WidgetPalette {

    /** Card surface, and the presence widget's ring when there is no status to show. */
    val Panel = ClumoColors.Background
    val Text = ClumoColors.Text
    val Muted = ClumoColors.Muted
    val NeutralButton = ClumoColors.SegBackground

    val OffDotArgb = ClumoColors.OffDot.toArgb()
    val PlaceholderStrokeArgb = ClumoColors.DashedBorder.toArgb()

    /** The enclosure is tinted, so a dot unlit against it needs a lighter off than the card. */
    const val OffDotOnEnclosureArgb: Int = 0x33FFFFFF

    val RingFailed = ClumoColors.Coral
    val RingIdle = ClumoColors.OutlineBorder
    val EnclosureOffline = Color(0xFF9AA79A)
    val KnobOffline = Color(0xFFE8E5E0)
}
