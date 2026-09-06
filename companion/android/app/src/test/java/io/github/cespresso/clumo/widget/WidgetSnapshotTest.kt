package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.accentContentToneFor
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {

    private fun snapshot(updatedAt: Long) =
        WidgetSnapshot(
            link = WidgetLink.Ready,
            headline = WidgetHeadline.PomodoroWorking,
            subtitle = WidgetSubtitle.Alias,
            subtitleText = "つくえ",
            faceBits = -1L shl 24,
            actions = listOf(WidgetAction.Pause, WidgetAction.Reset),
            enclosureArgb = 0xFF7E9E7C.toInt(),
            ctaArgb = 0xFFE8907E.toInt(),
            onCtaArgb = 0xFFFFFFFF.toInt(),
            knobArgb = 0xFFFFFFFF.toInt(),
            ledArgb = 0xFFF0A35E.toInt(),
            updatedAtRealtime = updatedAt,
        )

    @Test
    fun contentEqualityIgnoresTheTimestamp() {
        assertTrue(snapshot(1_000L).sameContentAs(snapshot(9_999_000L)))
    }

    @Test
    fun contentEqualityStillSeesRealChanges() {
        val paused = snapshot(1_000L).copy(headline = WidgetHeadline.Paused)
        assertFalse(snapshot(1_000L).sameContentAs(paused))
    }

    @Test
    fun freshSnapshotIsNotStale() {
        assertFalse(snapshot(1_000L).isStale(nowRealtime = 1_000L + 60_000L))
    }

    @Test
    fun snapshotPastTheThresholdIsStale() {
        assertTrue(snapshot(1_000L).isStale(nowRealtime = 1_000L + STALE_THRESHOLD_MS + 1L))
    }

    @Test
    fun aBackwardsClockMeansTheDeviceRebooted() {
        assertTrue(snapshot(9_999_000L).isStale(nowRealtime = 4_000L))
    }

    @Test
    fun heartbeatIsFrequentEnoughToKeepAnIdleSnapshotFresh() {
        assertTrue(HEARTBEAT_INTERVAL_MS * 2 <= STALE_THRESHOLD_MS)
    }

    @Test
    fun visualizerGlyphIsAFixedEightColumnBarChart() {
        // Bottom two rows are fully lit; the top row is empty.
        assertEquals(0xFFL shl 56, VISUALIZER_GLYPH and (0xFFL shl 56))
        assertEquals(0L, VISUALIZER_GLYPH and 0xFFL)
    }

    @Test
    fun disconnectedSnapshotIsTheSharedFallback() {
        val fallback = disconnectedSnapshot()
        assertEquals(WidgetHeadline.NotConnected, fallback.headline)
        assertEquals(WidgetSubtitle.TapToReconnect, fallback.subtitle)
        assertEquals(FaceBits.EMPTY, fallback.faceBits)
        assertEquals(listOf(WidgetAction.Retry), fallback.actions)
        assertEquals(0xFF7E9E7C.toInt(), fallback.enclosureArgb)
        assertEquals(0xFFE8907E.toInt(), fallback.ctaArgb)
        assertEquals(0xFFFFFFFF.toInt(), fallback.knobArgb)
        assertEquals(0xFFF0A35E.toInt(), fallback.ledArgb)
        // No device, so nothing to name; the widgets fall back to the headline.
        assertEquals("", fallback.alias)
    }

    @Test
    fun theFallbackIsTheDefaultAppearanceDisconnected() {
        // One definition of the disconnected layout, one source for the default colors.
        val expected = WidgetSnapshotFactory
            .identity(alias = "", appearance = DeviceAppearance.DEFAULT, nowRealtime = 0L)
            .asDisconnected()
        assertEquals(expected, disconnectedSnapshot())
        assertEquals(
            ContentTone.Light,
            accentContentToneFor(RgbColor.of(disconnectedSnapshot().ctaArgb)),
        )
    }

    @Test
    fun asDisconnectedKeepsIdentityAndClearsEverythingTheLinkReported() {
        val live = snapshot(1_000L).copy(
            alias = "つくえ",
            subtitleArgA = 25,
            subtitleArgB = 5,
            faceDimmed = true,
            backgroundTimerActive = true,
            family = WidgetFamily.Pomodoro,
        )
        val gone = live.asDisconnected()
        assertEquals("つくえ", gone.alias)
        assertEquals(live.enclosureArgb, gone.enclosureArgb)
        assertEquals(live.ctaArgb, gone.ctaArgb)
        assertEquals(live.onCtaArgb, gone.onCtaArgb)
        assertEquals(live.knobArgb, gone.knobArgb)
        assertEquals(live.ledArgb, gone.ledArgb)
        assertEquals(live.updatedAtRealtime, gone.updatedAtRealtime)

        assertEquals(WidgetLink.Connecting, gone.link)
        assertEquals(WidgetHeadline.NotConnected, gone.headline)
        assertEquals(WidgetSubtitle.TapToReconnect, gone.subtitle)
        assertEquals("", gone.subtitleText)
        assertEquals(0, gone.subtitleArgA)
        assertEquals(0, gone.subtitleArgB)
        assertEquals(FaceBits.EMPTY, gone.faceBits)
        assertFalse(gone.faceDimmed)
        assertFalse(gone.backgroundTimerActive)
        assertEquals(WidgetFamily.Neither, gone.family)
        assertEquals(listOf(WidgetAction.Retry), gone.actions)
    }
}
