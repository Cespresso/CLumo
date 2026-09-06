package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTimerStatusTest {
    @Test
    fun parsesCompletedStatus() {
        val status = CountdownTimerStatus.parse(byteArrayOf(3, 0, 0, 59, 59))!!

        assertTrue(status.isCompleted)
        assertEquals("00:00", status.formatRemaining())
        assertEquals(3599, status.configuredTotalSec)
    }

    @Test
    fun rejectsInvalidStatus() {
        assertNull(CountdownTimerStatus.parse(byteArrayOf(0, 0, 1, 60, 0)))
        assertNull(CountdownTimerStatus.parse(byteArrayOf(0, 0, 1, 0, 60)))
        assertNull(CountdownTimerStatus.parse(byteArrayOf(0, 0, 1, 0, 0)))
        assertNull(CountdownTimerStatus.parse(byteArrayOf(4, 0, 1, 0, 1)))
        assertNull(CountdownTimerStatus.parse(byteArrayOf(0, 0)))
    }
}
