package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PomodoroStatusTest {
    @Test
    fun parsesSixByteStatus() {
        val status = PomodoroStatus.parse(byteArrayOf(1, 0, 0, 59, 25, 5))

        assertEquals(PomodoroStatus.STATE_RUNNING, status?.state)
        assertEquals(59, status?.remainingSec)
        assertEquals(25, status?.workMin)
        assertEquals(5, status?.breakMin)
    }

    @Test
    fun rejectsShortStatus() {
        assertNull(PomodoroStatus.parse(byteArrayOf(1, 0, 0)))
    }
}
