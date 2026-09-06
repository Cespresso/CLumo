package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAppearanceViewModelTest {

    @Test
    fun successfulSaveKeepsOptimisticAppearance() =
        runTest {
            val next = DeviceAppearance.DEFAULT.copy(ledColor = color("#123456"))

            val result = saveAppearanceOptimistically(
                persisted = DeviceAppearance.DEFAULT,
                next = next,
                persist = {},
            )

            assertEquals(next, result.appearance)
            assertFalse(result.saveFailed)
        }

    @Test
    fun failedSaveRollsBackToPersistedAppearance() =
        runTest {
            val next = DeviceAppearance.DEFAULT.copy(ledColor = color("#123456"))

            val result = saveAppearanceOptimistically(
                persisted = DeviceAppearance.DEFAULT,
                next = next,
                persist = { error("storage failed") },
            )

            assertEquals(DeviceAppearance.DEFAULT, result.appearance)
            assertTrue(result.saveFailed)
        }

    @Test
    fun cancelledSaveRemainsCancellationRatherThanAStorageFailure() =
        runTest {
            var cancelled = false
            try {
                saveAppearanceOptimistically(
                    persisted = DeviceAppearance.DEFAULT,
                    next = DeviceAppearance.DEFAULT,
                    persist = { throw CancellationException("superseded") },
                )
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }

    private fun color(value: String) = requireNotNull(RgbColor.parseOrNull(value))
}
