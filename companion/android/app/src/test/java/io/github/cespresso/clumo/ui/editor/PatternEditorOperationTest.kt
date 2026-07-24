package io.github.cespresso.clumo.ui.editor

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PatternEditorOperationTest {

    @Test
    fun navigationWaitsUntilPersistenceCompletes() = runBlocking {
        val persistenceCompleted = CompletableDeferred<Unit>()
        var navigated = false

        val operation = launch {
            runPatternEditorOperation(
                persist = { persistenceCompleted.await() },
                onSuccess = { navigated = true },
                onFailure = { fail("Unexpected failure: $it") },
            )
        }

        yield()
        assertFalse(navigated)

        persistenceCompleted.complete(Unit)
        operation.join()

        assertTrue(navigated)
    }

    @Test
    fun persistenceFailureDoesNotNavigate() = runBlocking {
        val expected = IOException("write failed")
        var navigated = false
        var actual: Exception? = null

        runPatternEditorOperation(
            persist = { throw expected },
            onSuccess = { navigated = true },
            onFailure = { actual = it },
        )

        assertFalse(navigated)
        assertSame(expected, actual)
    }

    @Test
    fun cancellationIsRethrownWithoutReportingAPersistenceFailure() = runBlocking {
        val expected = CancellationException("screen removed")
        var failureReported = false

        try {
            runPatternEditorOperation(
                persist = { throw expected },
                onSuccess = { fail("Navigation must not run") },
                onFailure = { failureReported = true },
            )
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }

        assertFalse(failureReported)
    }

    @Test
    fun navigationFailureIsNotReportedAsAPersistenceFailure() = runBlocking {
        val expected = IllegalStateException("navigation failed")
        var failureReported = false

        try {
            runPatternEditorOperation(
                persist = {},
                onSuccess = { throw expected },
                onFailure = { failureReported = true },
            )
            fail("Navigation failure must be propagated")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }

        assertFalse(failureReported)
    }
}
