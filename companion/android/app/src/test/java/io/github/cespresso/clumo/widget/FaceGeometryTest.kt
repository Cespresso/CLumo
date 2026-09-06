package io.github.cespresso.clumo.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGeometryTest {

    @Test
    fun producesOneDotPerCell() {
        assertEquals(64, faceDots(bits = 0L, sizePx = 128).size)
    }

    @Test
    fun litFollowsTheBitmaskInRowMajorOrder() {
        // Bit 0 is the top-left cell.
        val dots = faceDots(bits = 1L, sizePx = 128)
        assertTrue(dots[0].lit)
        assertTrue(dots.drop(1).none { it.lit })
    }

    @Test
    fun allBitsSetLightsEveryDot() {
        assertTrue(faceDots(bits = -1L, sizePx = 128).all { it.lit })
    }

    @Test
    fun dotsStayInsideTheBitmap() {
        val size = 96
        faceDots(bits = -1L, sizePx = size).forEach { dot ->
            assertTrue(dot.centerX - dot.radius >= 0f)
            assertTrue(dot.centerY - dot.radius >= 0f)
            assertTrue(dot.centerX + dot.radius <= size.toFloat())
            assertTrue(dot.centerY + dot.radius <= size.toFloat())
        }
    }

    @Test
    fun theGridIsEvenlySpacedAndSquare() {
        val dots = faceDots(bits = 0L, sizePx = 128)
        val topLeft = dots[0]
        val nextInRow = dots[1]
        val nextInColumn = dots[8]
        val horizontal = nextInRow.centerX - topLeft.centerX
        val vertical = nextInColumn.centerY - topLeft.centerY
        assertEquals(horizontal, vertical, 0.001f)
        assertEquals(topLeft.centerY, nextInRow.centerY, 0.001f)
        assertEquals(topLeft.centerX, nextInColumn.centerX, 0.001f)
    }

    @Test
    fun radiusScalesWithSize() {
        val small = faceDots(bits = 0L, sizePx = 64).first().radius
        val large = faceDots(bits = 0L, sizePx = 128).first().radius
        assertEquals(2f, large / small, 0.001f)
    }
}
