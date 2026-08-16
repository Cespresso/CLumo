package io.github.cespresso.clumo.ui.device

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceViewModelTest {

    @Test
    fun explicitApplyPersistsDeviceSelectionBeforeCommittingFrame() = runTest {
        val pattern = Pattern("pattern", "Pattern", "1".repeat(64))
        val calls = mutableListOf<String>()

        applyPatternToDevice(
            deviceId = "device",
            pattern = pattern,
            setApplied = { deviceId, patternId -> calls += "persist:$deviceId:$patternId" },
            commit = { calls += "commit:${it.id}" },
        )

        assertEquals(listOf("persist:device:pattern", "commit:pattern"), calls)
    }

    @Test
    fun failedSelectionPersistenceDoesNotClaimFrameWasCommitted() = runTest {
        val pattern = Pattern("pattern", "Pattern", "1".repeat(64))
        var committed = false

        runCatching {
            applyPatternToDevice(
                deviceId = "device",
                pattern = pattern,
                setApplied = { _, _ -> error("storage failed") },
                commit = { committed = true },
            )
        }

        assertFalse(committed)
    }

    @Test
    fun initialUiStateIsDisconnectedAndDoesNotInventObservedPixels() {
        val state = DeviceUiStateFactory.initial("AA:BB")

        assertEquals("AA:BB", state.displayName)
        assertEquals(ConnectionState.Disconnected, state.link)
        assertFalse(state.ready)
        assertEquals(FaceBits.EMPTY, state.mirrorBits)
    }
}
