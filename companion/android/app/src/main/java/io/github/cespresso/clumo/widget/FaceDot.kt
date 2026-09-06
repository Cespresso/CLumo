package io.github.cespresso.clumo.widget

/** One dot of the 8x8 face, positioned inside a square grid. */
data class FaceDot(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val lit: Boolean,
)

private const val GRID = 8

/** Matches DotGrid, so the same face reads at the same weight in the app and on the widget. */
private const val DOT_RADIUS = 0.38f

/**
 * Dot positions for a square face. Kept free of Canvas so spacing and bounds stay testable:
 * android.graphics is stubbed in JVM unit tests.
 */
fun faceDots(bits: Long, sizePx: Int): List<FaceDot> {
    val cell = sizePx.toFloat() / GRID
    val radius = cell * DOT_RADIUS
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
                    ),
                )
            }
        }
    }
}
