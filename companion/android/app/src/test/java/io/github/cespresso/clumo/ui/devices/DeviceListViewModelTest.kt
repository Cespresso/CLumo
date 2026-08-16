package io.github.cespresso.clumo.ui.devices

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.DeviceSessionState
import io.github.cespresso.clumo.domain.DeviceSnapshot
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.ScanEvent
import io.github.cespresso.clumo.domain.ScanFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceListViewModelTest {

    @Test
    fun cardsResolveAppliedPatternPerDevice() {
        val first = Device("device-a", "AA", "CLumo-A", 2)
        val second = Device("device-b", "BB", "CLumo-B", 1)
        val firstPattern = Pattern("p1", "One", "1" + "0".repeat(63))
        val secondPattern = Pattern("p2", "Two", "0".repeat(63) + "1")
        val displayState = DeviceSessionState(
            link = ConnectionState.Ready,
            observed = DeviceSnapshot(DeviceMode.DISPLAY, 15),
        )

        val cards = buildKnownDeviceCards(
            devices = listOf(first, second),
            liveStates = mapOf(
                "AA" to DeviceCardLiveState(displayState),
                "BB" to DeviceCardLiveState(displayState),
            ),
            aliases = emptyMap(),
            appearances = emptyMap(),
            primaryDeviceId = "device-a",
            patterns = listOf(firstPattern, secondPattern),
            appliedPatternIds = mapOf("device-a" to "p1", "device-b" to "p2"),
        )

        assertEquals(FaceBits.fromBitsString(firstPattern.bits), cards[0].mirrorBits)
        assertEquals(FaceBits.fromBitsString(secondPattern.bits), cards[1].mirrorBits)
        assertEquals(true, cards[0].isPrimary)
    }

    @Test
    fun scanReducerDeduplicatesAdvertisementsAndStopsOnFailure() {
        val first = DeviceAdvertisement("AA", "Old", -80)
        val newer = DeviceAdvertisement("AA", "New", -50)
        var state = DeviceScanState(scanning = true)

        state = reduceScanEvent(state, ScanEvent.DeviceFound(first))
        state = reduceScanEvent(state, ScanEvent.DeviceFound(newer))
        state = reduceScanEvent(state, ScanEvent.Failed(ScanFailure.ScanFailed))

        assertEquals(newer, state.advertisements.getValue("AA"))
        assertEquals(ScanFailure.ScanFailed, state.failure)
        assertFalse(state.scanning)
    }
}
