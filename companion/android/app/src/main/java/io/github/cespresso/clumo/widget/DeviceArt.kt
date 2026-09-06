package io.github.cespresso.clumo.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.contentToneFor
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.RgbColor

// The app's knob ratios are fractions of a 168dp frame while its face ratios are fractions of
// 188dp. Both scale off the same face size, so they compose correctly. The denominators are
// not interchangeable; see ClumoDevice.
private const val KNOB_REF = 168f
private const val FACE_REF = 188f

/** Where each piece of the device sits in a bitmap sized from its face. */
data class DeviceArtLayout(
    val widthPx: Float,
    val heightPx: Float,
    val facePx: Float,
    val faceLeft: Float,
    val faceTop: Float,
    val frameCorner: Float,
    val framePadding: Float,
    val panelCorner: Float,
    val gridPadding: Float,
    val knobCapWidth: Float,
    val knobCapHeight: Float,
    val knobBossWidth: Float,
    val knobBossHeight: Float,
    val knobCapCorner: Float,
    val knobBossCorner: Float,
    val knobGap: Float,
    val ringInset: Float,
    val ringStroke: Float,
)

/**
 * Proportions taken from the app's device art, so the same CLumo appears on the home screen
 * and in the app. The ring is scaled off the face rather than fixed in dp: the app draws it
 * around a device several times this size, where a fixed width still reads.
 */
fun deviceArtLayout(facePx: Float, ringed: Boolean): DeviceArtLayout {
    val capHeight = facePx * 10f / KNOB_REF
    val bossHeight = facePx * 8f / KNOB_REF
    val ringStroke = if (ringed) facePx * 0.05f else 0f
    val ringInset = if (ringed) ringStroke + facePx * 0.035f else 0f
    val knobBand = capHeight + bossHeight
    return DeviceArtLayout(
        widthPx = facePx + ringInset * 2f,
        heightPx = knobBand + facePx + ringInset,
        facePx = facePx,
        faceLeft = ringInset,
        faceTop = knobBand,
        frameCorner = facePx * 42f / FACE_REF,
        framePadding = facePx * 23f / FACE_REF,
        panelCorner = facePx * 21f / FACE_REF,
        gridPadding = facePx * 13f / FACE_REF,
        knobCapWidth = facePx * 18f / KNOB_REF,
        knobCapHeight = capHeight,
        knobBossWidth = facePx * 28f / KNOB_REF,
        knobBossHeight = bossHeight,
        knobCapCorner = facePx * 6f / KNOB_REF,
        knobBossCorner = facePx * 5f / KNOB_REF,
        knobGap = facePx * 16f / KNOB_REF,
        ringInset = ringInset,
        ringStroke = ringStroke,
    )
}

/**
 * The whole device as one bitmap: ring, knobs, enclosure, inner panel and dots. A Glance Box
 * per piece would cost RemoteViews nodes from a budget the two widgets share, and nothing
 * short of a canvas can draw the knobs' two tiers.
 *
 * Both widgets render through here, so a CLumo with the link down cannot look alive on one and
 * dead on the other. Only [ringed] differs: the control widget states the link in words.
 */
internal fun renderDeviceBitmap(
    snapshot: WidgetSnapshot,
    facePx: Int,
    ringed: Boolean,
): Bitmap {
    val connected = snapshot.link == WidgetLink.Ready
    val enclosureArgb =
        if (connected) snapshot.enclosureArgb else WidgetPalette.EnclosureOffline.toArgb()
    val knobAArgb = if (connected) snapshot.ctaArgb else WidgetPalette.KnobOffline.toArgb()
    val knobBArgb = if (connected) snapshot.knobArgb else WidgetPalette.KnobOffline.toArgb()
    // A dark link means dark LEDs. The widget reports the device, it does not decorate it.
    val bits = if (connected) snapshot.faceBits else FaceBits.EMPTY
    val ringArgb = when {
        !ringed || connected -> null
        snapshot.link == WidgetLink.Failed -> WidgetPalette.RingFailed.toArgb()
        else -> WidgetPalette.RingIdle.toArgb()
    }
    val layout = deviceArtLayout(facePx.toFloat(), ringed = ringed)
    val bitmap = createBitmap(
        layout.widthPx.toInt().coerceAtLeast(1),
        layout.heightPx.toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (ringArgb != null) drawRing(canvas, paint, layout, ringArgb)
    // Before the frame, which paints over the tuck that hides the seam between them.
    drawKnobs(canvas, paint, layout, knobAArgb, knobBArgb)
    drawEnclosure(canvas, paint, layout, enclosureArgb)
    drawScreen(
        canvas = canvas,
        paint = paint,
        layout = layout,
        bits = bits,
        ledArgb = snapshot.ledArgb,
        dimmed = snapshot.faceDimmed,
        placeholder = snapshot.facePlaceholder,
    )
    if (connected && snapshot.backgroundTimerActive) {
        drawBackgroundTimerBadge(canvas, paint, layout, snapshot.ledArgb)
    }
    return bitmap
}

/** Marks a Pomodoro or Timer counting down in a mode other than the one being shown. */
private fun drawBackgroundTimerBadge(canvas: Canvas, paint: Paint, layout: DeviceArtLayout, argb: Int) {
    val radius = layout.facePx * 0.045f
    val cx = layout.faceLeft + layout.facePx - layout.frameCorner - radius - layout.facePx * 0.02f
    val cy = layout.faceTop + layout.facePx * 0.07f
    paint.reset()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    paint.color = argb
    canvas.drawCircle(cx, cy, radius, paint)
}

private fun drawRing(canvas: Canvas, paint: Paint, layout: DeviceArtLayout, argb: Int) {
    val half = layout.ringStroke / 2f
    val gap = layout.ringInset - half
    val bounds = RectF(
        layout.faceLeft - gap,
        layout.faceTop - gap,
        layout.faceLeft + layout.facePx + gap,
        layout.faceTop + layout.facePx + gap,
    )
    paint.reset()
    paint.isAntiAlias = true
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = layout.ringStroke
    paint.color = argb
    val corner = layout.frameCorner + gap
    canvas.drawRoundRect(bounds, corner, corner, paint)
}

private fun drawKnobs(
    canvas: Canvas,
    paint: Paint,
    layout: DeviceArtLayout,
    knobAArgb: Int,
    knobBArgb: Int,
) {
    val pairWidth = layout.knobBossWidth * 2f + layout.knobGap
    val left = (layout.widthPx - pairWidth) / 2f
    drawKnob(canvas, paint, layout, left, knobAArgb)
    drawKnob(canvas, paint, layout, left + layout.knobBossWidth + layout.knobGap, knobBArgb)
}

/**
 * One knob as a single convex path so the cap and the boss share an outline. The tier step
 * reads from the silhouette plus a shading gradient, with light on each tier's top edge and
 * shade at its base.
 */
private fun drawKnob(
    canvas: Canvas,
    paint: Paint,
    layout: DeviceArtLayout,
    bossLeft: Float,
    argb: Int,
) {
    val capLeft = bossLeft + (layout.knobBossWidth - layout.knobCapWidth) / 2f
    val capBottom = layout.knobCapHeight
    // Run the boss a hair into the frame so the frame's own fill hides the join.
    val bossBottom = layout.faceTop + layout.facePx / KNOB_REF

    val cap = Path().apply {
        addRoundRect(
            RectF(capLeft, 0f, capLeft + layout.knobCapWidth, capBottom + 1f),
            floatArrayOf(
                layout.knobCapCorner,
                layout.knobCapCorner,
                layout.knobCapCorner,
                layout.knobCapCorner,
                0f,
                0f,
                0f,
                0f,
            ),
            Path.Direction.CW,
        )
    }
    val boss = Path().apply {
        addRoundRect(
            RectF(bossLeft, capBottom, bossLeft + layout.knobBossWidth, bossBottom),
            floatArrayOf(
                layout.knobBossCorner,
                layout.knobBossCorner,
                layout.knobBossCorner,
                layout.knobBossCorner,
                0f,
                0f,
                0f,
                0f,
            ),
            Path.Direction.CW,
        )
    }
    cap.op(boss, Path.Op.UNION)

    val junction = capBottom / bossBottom
    paint.reset()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        bossBottom,
        intArrayOf(
            ColorUtils.blendARGB(argb, Color.WHITE, 0.10f),
            ColorUtils.blendARGB(argb, Color.BLACK, 0.07f),
            ColorUtils.blendARGB(argb, Color.WHITE, 0.14f),
            ColorUtils.blendARGB(argb, Color.BLACK, 0.09f),
        ),
        floatArrayOf(0f, junction * 0.97f, junction, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawPath(cap, paint)
    paint.shader = null

    outlineFor(argb)?.let { outline ->
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = layout.facePx / FACE_REF
        paint.color = outline
        canvas.drawPath(cap, paint)
    }
}

private fun drawEnclosure(
    canvas: Canvas,
    paint: Paint,
    layout: DeviceArtLayout,
    enclosureArgb: Int,
) {
    val frame = RectF(
        layout.faceLeft,
        layout.faceTop,
        layout.faceLeft + layout.facePx,
        layout.faceTop + layout.facePx,
    )
    paint.reset()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    paint.color = enclosureArgb
    canvas.drawRoundRect(frame, layout.frameCorner, layout.frameCorner, paint)
    outlineFor(enclosureArgb)?.let { outline ->
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = layout.facePx / FACE_REF
        paint.color = outline
        canvas.drawRoundRect(frame, layout.frameCorner, layout.frameCorner, paint)
    }
}

private fun drawScreen(
    canvas: Canvas,
    paint: Paint,
    layout: DeviceArtLayout,
    bits: Long,
    ledArgb: Int,
    dimmed: Boolean,
    placeholder: Boolean,
) {
    val panel = RectF(
        layout.faceLeft + layout.framePadding,
        layout.faceTop + layout.framePadding,
        layout.faceLeft + layout.facePx - layout.framePadding,
        layout.faceTop + layout.facePx - layout.framePadding,
    )
    paint.reset()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    paint.color = ClumoColors.Panel.toArgb()
    canvas.drawRoundRect(panel, layout.panelCorner, layout.panelCorner, paint)

    val gridLeft = panel.left + layout.gridPadding
    val gridTop = panel.top + layout.gridPadding
    val gridSize = panel.width() - layout.gridPadding * 2f

    if (placeholder) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = gridSize * 0.045f
        paint.color = ClumoColors.DashedBorder.toArgb()
        paint.pathEffect = DashPathEffect(floatArrayOf(gridSize * 0.09f, gridSize * 0.07f), 0f)
        val inset = paint.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(gridLeft + inset, gridTop + inset, gridLeft + gridSize - inset, gridTop + gridSize - inset),
            gridSize * 0.14f,
            gridSize * 0.14f,
            paint,
        )
        paint.pathEffect = null
        return
    }

    val lit = if (dimmed) dim(ledArgb) else ledArgb
    val off = ClumoColors.OffDot.toArgb()
    faceDots(bits, gridSize.toInt()).forEach { dot ->
        val cx = gridLeft + dot.centerX
        val cy = gridTop + dot.centerY
        if (dot.lit) {
            // The same warm halo the app draws, which is most of why a lit face reads as lit.
            paint.shader = RadialGradient(
                cx,
                cy,
                dot.radius * GLOW_SPREAD,
                ColorUtils.setAlphaComponent(lit, (Color.alpha(lit) * 0.55f).toInt()),
                ColorUtils.setAlphaComponent(lit, 0),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, dot.radius * GLOW_SPREAD, paint)
            paint.shader = null
        }
        paint.color = if (dot.lit) lit else off
        canvas.drawCircle(cx, cy, dot.radius, paint)
    }
}

private const val GLOW_SPREAD = 2.1f

/** A light fill needs a hairline so it does not dissolve into the widget's card. */
private fun outlineFor(argb: Int): Int? =
    if (contentToneFor(RgbColor.of(argb)) == ContentTone.Dark) {
        ClumoColors.OutlineBorder.toArgb()
    } else {
        null
    }

private fun dim(argb: Int): Int =
    Color.argb(
        (Color.alpha(argb) * 0.38f).toInt(),
        Color.red(argb),
        Color.green(argb),
        Color.blue(argb),
    )
