package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.domain.FaceBits
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a widget is allowed to wake up for. */
class WidgetSessionTest {

    private fun snapshot(updatedAt: Long) =
        WidgetSnapshot(
            link = WidgetLink.Ready,
            headline = WidgetHeadline.PomodoroWorking,
            subtitle = WidgetSubtitle.Alias,
            alias = "つくえ",
            faceBits = -1L shl 24,
            actions = listOf(WidgetAction.Pause, WidgetAction.Reset),
            enclosureArgb = 0xFF7E9E7C.toInt(),
            ctaArgb = 0xFFE8907E.toInt(),
            onCtaArgb = 0xFFFFFFFF.toInt(),
            knobArgb = 0xFFFFFFFF.toInt(),
            ledArgb = 0xFFF0A35E.toInt(),
            updatedAtRealtime = updatedAt,
        )

    /** Past every timestamp below, and still inside the staleness threshold for all of them. */
    private val now = 1_000L + HEARTBEAT_INTERVAL_MS * 2 + 1_000L

    private fun collect(
        vararg emissions: WidgetSnapshot?,
        clock: () -> Long = { now },
    ): List<WidgetSnapshot?> =
        runBlocking {
            flowOf(*emissions).agedContentOnly(clock).toList()
        }

    @Test
    fun theHeartbeatDoesNotReachTheWidget() {
        // Same content, later timestamp: the service is only saying it is still alive.
        val emitted = collect(
            snapshot(1_000L),
            snapshot(1_000L + HEARTBEAT_INTERVAL_MS),
            snapshot(1_000L + HEARTBEAT_INTERVAL_MS * 2),
        )
        assertEquals(1, emitted.size)
        assertEquals(1_000L, emitted.single()?.updatedAtRealtime)
    }

    @Test
    fun aContentChangeStillReachesTheWidget() {
        val emitted = collect(
            snapshot(1_000L),
            snapshot(2_000L).copy(headline = WidgetHeadline.Paused),
        )
        assertEquals(2, emitted.size)
        assertEquals(WidgetHeadline.Paused, emitted.last()?.headline)
    }

    @Test
    fun goingStaleStillReachesTheWidget() {
        // The aging runs before the timestamp filter, so an unchanged snapshot whose clock
        // has run out still moves the widget, to its device with the link gone.
        var now = 1_000L
        val emitted = collect(snapshot(500L), snapshot(500L), clock = {
            now.also { now += STALE_THRESHOLD_MS }
        })
        assertEquals(listOf(snapshot(500L), snapshot(500L).asDisconnected()), emitted)
    }

    @Test
    fun aStaleSeedIsDrawnAsItsDeviceDisconnected() {
        val aged = snapshot(500L).aged(500L + STALE_THRESHOLD_MS + 1L)
        assertEquals(snapshot(500L).asDisconnected(), aged)
        assertEquals(snapshot(500L), snapshot(500L).aged(600L))
    }

    @Test
    fun agingKeepsTheIdentityAndDropsTheLink() {
        val aged = snapshot(500L).aged(500L + STALE_THRESHOLD_MS + 1L)
        // The colors and the name came from preferences, which do not expire.
        assertEquals("つくえ", aged.alias)
        assertEquals(0xFF7E9E7C.toInt(), aged.enclosureArgb)
        assertEquals(0xFFE8907E.toInt(), aged.ctaArgb)
        // The pomodoro the link was reporting is what went stale.
        assertEquals(WidgetHeadline.NotConnected, aged.headline)
        assertEquals(listOf(WidgetAction.Retry), aged.actions)
        assertEquals(FaceBits.EMPTY, aged.faceBits)
    }
}
