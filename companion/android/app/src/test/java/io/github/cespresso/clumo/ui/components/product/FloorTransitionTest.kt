package io.github.cespresso.clumo.ui.components.product

import org.junit.Assert.assertEquals
import org.junit.Test

class FloorTransitionTest {
    @Test fun assignmentCameraRetracesSameProgressWhenClosingMidway() {
        val reveal = ReversibleReveal(AssignmentMotion.DURATION)
        reveal.advance(.3f, true)
        val outward = AssignmentMotion.cameraFraction(reveal.progress)
        reveal.advance(.2f, true)
        reveal.advance(.2f, false)
        assertEquals(outward, AssignmentMotion.cameraFraction(reveal.progress), .0001f)
        reveal.advance(1f, false)
        assertEquals(0f, AssignmentMotion.cameraFraction(reveal.progress), 0f)
    }

    @Test fun repeatedCloseCompletesOnlyOnce() {
        val close = CloseRequest()
        var calls = 0
        val token = close.request { calls++ }
        assertEquals(token, close.request { calls += 100 })
        close.complete(token)
        close.complete(token)
        assertEquals(1, calls)
    }

    @Test fun cancelledCompletionCannotCloseNewSelection() {
        val close = CloseRequest()
        var calls = 0
        val old = close.request { calls += 100 }
        close.cancel()
        val current = close.request { calls++ }
        close.complete(old)
        assertEquals(0, calls)
        close.complete(current)
        assertEquals(1, calls)
    }

    @Test fun completionCanRequestAnotherClose() {
        val close = CloseRequest()
        var next = 0L
        var calls = 0
        val token = close.request { next = close.request { calls++ } }
        close.complete(token)
        close.complete(token)
        close.complete(next)
        assertEquals(1, calls)
    }

    @Test fun interruptedRevealReversesFromCurrentProgress() {
        val reveal = ReversibleReveal(.62f)
        reveal.advance(.2f, true)
        val halfway = reveal.progress
        reveal.advance(.1f, false)
        assertEquals(halfway - .1f / .62f, reveal.progress, .0001f)
        reveal.advance(.1f, true)
        assertEquals(halfway, reveal.progress, .0001f)
        reveal.advance(2f, false)
        assertEquals(0f, reveal.progress, 0f)
        reveal.advance(2f, false)
        assertEquals(0f, reveal.progress, 0f)
        reveal.advance(2f, true)
        assertEquals(1f, reveal.progress, 0f)
    }
}
