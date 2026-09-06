package io.github.cespresso.clumo.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotJsonTest {

    private val snapshot = WidgetSnapshot(
        link = WidgetLink.Ready,
        headline = WidgetHeadline.PomodoroWorking,
        subtitle = WidgetSubtitle.Alias,
        subtitleText = "つくえ の CLumo",
        subtitleArgA = 25,
        subtitleArgB = 5,
        alias = "しごとべや の CLumo",
        faceBits = -1L shl 24,
        faceDimmed = true,
        facePlaceholder = false,
        family = WidgetFamily.Pomodoro,
        actions = listOf(WidgetAction.Pause, WidgetAction.Reset),
        enclosureArgb = 0xFF7E9E7C.toInt(),
        ctaArgb = 0xFFE8907E.toInt(),
        onCtaArgb = 0xFF3E3A36.toInt(),
        knobArgb = 0xFFFFFFFF.toInt(),
        ledArgb = 0xFFF0A35E.toInt(),
        updatedAtRealtime = 123_456L,
    )

    @Test
    fun roundTripsEveryField() {
        assertEquals(snapshot, decodeWidgetSnapshot(encodeWidgetSnapshot(snapshot)))
    }

    @Test
    fun theAliasIsCarriedSeparatelyFromTheSubtitle() {
        // Both slots hold free text, so a decoder that crossed them would still produce
        // a plausible snapshot. Assert they arrive distinct.
        val decoded = decodeWidgetSnapshot(encodeWidgetSnapshot(snapshot))
        assertEquals("しごとべや の CLumo", decoded?.alias)
        assertEquals("つくえ の CLumo", decoded?.subtitleText)
    }

    @Test
    fun theCtaContentColorSurvivesTheRoundTrip() {
        val decoded = decodeWidgetSnapshot(encodeWidgetSnapshot(snapshot))
        assertEquals(0xFF3E3A36.toInt(), decoded?.onCtaArgb)
    }

    @Test
    fun facePlaceholderSurvivesTheRoundTripWhenSet() {
        // The fixture leaves it at its default, so only this exercises the true case:
        // dropping the field from either side would otherwise pass unnoticed and swap a
        // dashed placeholder for an unlit face.
        val placeholder = snapshot.copy(facePlaceholder = true)
        val decoded = decodeWidgetSnapshot(encodeWidgetSnapshot(placeholder))
        assertTrue(decoded!!.facePlaceholder)
        assertEquals(placeholder, decoded)
    }

    @Test
    fun roundTripsAnEmptyActionList() {
        val quiet = snapshot.copy(actions = emptyList())
        assertEquals(quiet, decodeWidgetSnapshot(encodeWidgetSnapshot(quiet)))
    }

    @Test
    fun negativeFaceBitsSurviveTheRoundTrip() {
        // -1L is "all 64 pixels lit" and must not be mangled by JSON number handling.
        val full = snapshot.copy(faceBits = -1L)
        assertEquals(-1L, decodeWidgetSnapshot(encodeWidgetSnapshot(full))?.faceBits)
    }

    @Test
    fun malformedInputDecodesToNothing() {
        assertNull(decodeWidgetSnapshot(null))
        assertNull(decodeWidgetSnapshot(""))
        assertNull(decodeWidgetSnapshot("{"))
        assertNull(decodeWidgetSnapshot("""{"link":"NotALink"}"""))
    }
}
