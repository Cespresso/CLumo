package io.github.cespresso.clumo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.DeviceAppearance

/** Decorative hardware silhouette; the three volume windows are not live telemetry. */
@Composable
internal fun HeadphoneStandDevice(
    bits: Long,
    size: Dp,
    appearance: DeviceAppearance,
    modifier: Modifier = Modifier,
    litAlpha: Float = 1f,
) {
    val description = stringResource(R.string.stand_device_illustration)
    Canvas(modifier = modifier.size(size).semantics { contentDescription = description }) {
        // Front projection of clumo_tower_main.step, mm: X right, Z up.
        // View bounds X=-40..40, Z=5..85; panel is 73 mm square.
        val u = this.size.width / 80f
        fun point(x: Float, z: Float) = Offset((x + 40f) * u, (85f - z) * u)
        fun panel(x: Float, zTop: Float, w: Float, h: Float, radius: Float, color: Color) {
            drawRoundRect(color, point(x, zTop), Size(w * u, h * u), CornerRadius(radius * u))
        }
        val shell = appearance.enclosureColor.toComposeColor()
        val accent = appearance.buttonBColor.toComposeColor()
        val buttons = appearance.buttonAColor.toComposeColor()
        panel(-39f, 84f, 78f, 78f, 6f, shell)
        panel(-36.5f, 81.5f, 73f, 73f, 5.6f, appearance.panelColor.toComposeColor())
        // Matrix surface and frame interrupt the recessed L-shaped control panel.
        panel(-37.5f, 82.5f, 45.2f, 45.2f, 6f, shell)
        panel(-36.5f, 81.5f, 43.2f, 43.2f, 5.6f, appearance.matrixSurfaceColor.toComposeColor())
        val led = appearance.ledColor.toComposeColor()
        for (y in 0..7) {
            for (x in 0..7) {
                val on = bits and (1L shl (y * 8 + x)) != 0L
                drawCircle(if (on) led.copy(alpha = litAlpha) else ClumoColors.OffDot, 1.3f * u, point(-28.9f + x * 4f, 73.9f - y * 4f))
            }
        }
        // Visible lens faces are 6 mm squares; rear flanges are 8 mm.
        repeat(3) { panel(19.1f, 72.45f - it * 9f, 6f, 6f, .8f, appearance.windowColor.toComposeColor()) }
        panel(19.1f, 45.45f, 6f, 6f, .8f, accent)
        panel(-28.85f, 31.9f, 1.8f, 18f, .9f, accent)
        for (x in listOf(-16.4f, .6f)) {
            drawCircle(shell, 4.5f * u, point(x, 22.9f))
            drawCircle(buttons, 3.7f * u, point(x, 22.9f))
            drawCircle(Color(0x33000000), 3.7f * u, point(x, 22.9f), style = Stroke(.25f * u))
        }
        drawCircle(accent, 9f * u, point(22.1f, 22.9f))
        drawCircle(Color(0x22000000), 9f * u, point(22.1f, 22.9f), style = Stroke(.35f * u))
    }
}
