package io.github.cespresso.clumo.ui.components.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentLayoutTest {
    @Test fun branchFollowsSelectedBindingAndRowsRevealInOrder() {
        assertEquals(.57f, 1.06f + AssignmentLayout.sourceOffset(5), .0001f)
        assertEquals(.815f, 1.06f + AssignmentLayout.sourceOffset(2), .0001f)
        assertEquals(1.305f, 1.06f + AssignmentLayout.sourceOffset(4), .0001f)
        val arrivals = (0..7).map { AssignmentLayout.rowThreshold(it) }
        assertTrue(arrivals.first() > 0f && arrivals.last() < 1f)
        assertTrue(arrivals.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun shortListFitsAndLongListCanReachLastRow() {
        for ((width, aspect) in listOf(360f to .45f, 800f to 2.1f, 600f to .75f)) {
            val end = AssignmentLayout.TOP_Z + AssignmentLayout.visibleDepth(width, aspect) + AssignmentLayout.maxScroll(8, width, aspect)
            assertTrue(end >= AssignmentLayout.rowZ(7) + AssignmentLayout.HALF_ROW)
        }
        assertEquals(0f, AssignmentLayout.maxScroll(2, 360f, .45f), .0001f)
        assertTrue(AssignmentLayout.maxScroll(8, 360f, .45f) > 0f)
    }

    @Test fun wideWindowsKeepPanelPhysicalScreenSize() {
        assertEquals(AssignmentLayout.width(380f) * 2, AssignmentLayout.width(760f), .0001f)
        assertEquals(AssignmentLayout.width(380f), AssignmentLayout.width(320f), .0001f)
    }
}
