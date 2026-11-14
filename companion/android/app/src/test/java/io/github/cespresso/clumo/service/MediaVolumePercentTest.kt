package io.github.cespresso.clumo.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumePercentTest {
    @Test
    fun mapsMediaStreamRangeAndUnknownValues() {
        assertEquals(0, mediaVolumePercent(0, 15))
        assertEquals(100, mediaVolumePercent(15, 15))
        assertEquals(47, mediaVolumePercent(7, 15))
        assertEquals(null, mediaVolumePercent(0, 0))
        assertEquals(null, mediaVolumePercent(-1, 15))
        assertEquals(null, mediaVolumePercent(16, 15))
    }
}
