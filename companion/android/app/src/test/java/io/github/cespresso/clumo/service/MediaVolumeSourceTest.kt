package io.github.cespresso.clumo.service

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaVolumeSourceTest {
    @Test
    fun reportsUnchangedValuesForLivenessAndRecoversFromReadFailure() =
        runTest {
            var reads = 0
            val source = MediaVolumeSource {
                when (reads++) {
                    0, 1 -> 0
                    2 -> error("temporarily unavailable")
                    3 -> 101
                    else -> 50
                }
            }
            assertEquals(listOf(0, 0, null, null, 50), source.snapshots().take(5).toList())
            assertEquals(2000L, testScheduler.currentTime)
        }
}
