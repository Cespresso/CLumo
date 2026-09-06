package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamWritePolicyTest {
    @Test
    fun aNewerFrameSupersedesOnlyPendingFramesOfItsOwnStream() {
        assertTrue(
            streamWriteMayBeCoalesced(BleUuids.DISPLAY_PREVIEW, pendingNoResponse = true, incomingUuid = BleUuids.DISPLAY_PREVIEW),
        )
        assertFalse(
            streamWriteMayBeCoalesced(BleUuids.DISPLAY_PREVIEW, pendingNoResponse = true, incomingUuid = BleUuids.VISUALIZER),
        )
    }

    @Test
    fun aQueuedCommitSurvivesAnIncomingPreview() {
        assertFalse(
            streamWriteMayBeCoalesced(BleUuids.DISPLAY_FRAME, pendingNoResponse = false, incomingUuid = BleUuids.DISPLAY_PREVIEW),
        )
        // Even a newer frame for the same characteristic leaves a reliable write alone.
        assertFalse(
            streamWriteMayBeCoalesced(BleUuids.DISPLAY_FRAME, pendingNoResponse = false, incomingUuid = BleUuids.DISPLAY_FRAME),
        )
    }
}
