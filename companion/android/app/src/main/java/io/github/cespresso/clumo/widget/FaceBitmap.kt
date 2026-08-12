package io.github.cespresso.clumo.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint

/** One dot of the 8x8 face, positioned inside a square bitmap. */
data class FaceDot(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val lit: Boolean,
)

private const val GRID = 8
private const val DOT_FILL = 0.72f

/**
 * Dot positions for a square face. Kept free of Canvas so spacing and bounds stay testable:
 * android.graphics is stubbed in JVM unit tests.
 */
fun faceDots(bits: Long, sizePx: Int): List<FaceDot> {
    val cell = sizePx.toFloat() / GRID
    val radius = cell * DOT_FILL / 2f
    return buildList(GRID * GRID) {
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val index = row * GRID + col
                add(
                    FaceDot(
                        centerX = cell * (col + 0.5f),
                        centerY = cell * (row + 0.5f),
                        radius = radius,
                        lit = (bits shr index) and 1L == 1L,
                    )
                )
            }
        }
    }
}

/** Renders the face as a single bitmap, so a widget spends one RemoteViews node on it. */
fun renderFaceBitmap(
    bits: Long,
    sizePx: Int,
    litArgb: Int,
    offArgb: Int,
    dimmed: Boolean,
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val litColor = if (dimmed) dim(litArgb) else litArgb
    faceDots(bits, sizePx).forEach { dot ->
        paint.color = if (dot.lit) litColor else offArgb
        canvas.drawCircle(dot.centerX, dot.centerY, dot.radius, paint)
    }
    return bitmap
}

/** A dashed outline for a widget with no device, which must not read as LEDs merely off. */
fun renderPlaceholderBitmap(sizePx: Int, strokeArgb: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * 0.045f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = strokeArgb
        pathEffect = DashPathEffect(floatArrayOf(sizePx * 0.09f, sizePx * 0.07f), 0f)
    }
    val inset = stroke / 2f
    val radius = sizePx * 0.14f
    canvas.drawRoundRect(inset, inset, sizePx - inset, sizePx - inset, radius, radius, paint)
    return bitmap
}

private fun dim(argb: Int): Int = Color.argb(
    (Color.alpha(argb) * 0.38f).toInt(),
    Color.red(argb),
    Color.green(argb),
    Color.blue(argb),
)
