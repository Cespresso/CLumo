package io.github.cespresso.clumo.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCommandTest {

    private val all = listOf(
        WidgetCommand.TogglePomodoro,
        WidgetCommand.ResetPomodoro,
        WidgetCommand.ToggleTimer,
        WidgetCommand.CancelTimer,
        WidgetCommand.Retry,
    )

    @Test
    fun everyCommandRoundTrips() {
        all.forEach { command ->
            assertEquals(command, WidgetCommand.decode(WidgetCommand.encode(command)))
        }
    }

    @Test
    fun encodingsAreDistinct() {
        assertEquals(all.size, all.map { WidgetCommand.encode(it) }.toSet().size)
    }

    @Test
    fun malformedInputDecodesToNothing() {
        assertNull(WidgetCommand.decode(null))
        assertNull(WidgetCommand.decode(""))
        assertNull(WidgetCommand.decode("NotACommand"))
    }
}
