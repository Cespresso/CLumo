package io.github.cespresso.clumo.ui.devices

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.DeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryGalleryTest {
    @Test
    fun advertisedNameDoesNotDetermineModel() {
        val state = DeviceListUiState(discoveredDevices = listOf(DeviceAdvertisement("AA:01", "CLumo-Stand-Test", -40)))
        val candidate = discoveryGallery(state).single()
        assertTrue(candidate.unidentified)
        assertEquals("models/unknown-tag-box.glb", candidate.asset(0))
        assertEquals("AA:01", candidate.address)
    }

    @Test
    fun onlyReadyKnownDeviceReplacesQuestionMarkAndKeepsSelectionId() {
        val device = Device("stable", "AA:01", "Stand", 0, kind = DeviceKind.HEADPHONE_STAND)
        val cards = buildKnownDeviceCards(listOf(device), emptyMap(), emptyMap(), emptyMap(), null, io.github.cespresso.clumo.ui.appearance.defaultStandAppearance)
        val base = DeviceListUiState(knownDevices = cards, discoveredDevices = listOf(DeviceAdvertisement(device.address, "Stand", -40)))
        for (link in listOf(ConnectionState.Connecting, ConnectionState.Synchronizing, ConnectionState.Error)) {
            assertTrue(discoveryGallery(base.copy(connectionStates = mapOf(device.address to link))).single().unidentified)
        }
        val ready = discoveryGallery(base.copy(connectionStates = mapOf(device.address to ConnectionState.Ready))).single()
        assertFalse(ready.unidentified)
        assertEquals("models/headphone-stand-live.glb", ready.asset(0))
        assertEquals(discoveryGallery(base).single().id, ready.id)
    }

    @Test
    fun sameNamesKeepDistinctAddressesAndFirstSeenOrder() {
        var scan = DeviceScanState()
        for (advertisement in listOf(DeviceAdvertisement("AA:01", "CLumo", -80), DeviceAdvertisement("BB:02", "CLumo", -30), DeviceAdvertisement("AA:01", "CLumo", -10))) {
            scan = reduceScanEvent(scan, io.github.cespresso.clumo.domain.ScanEvent.DeviceFound(advertisement))
        }
        val candidates = discoveryGallery(DeviceListUiState(discoveredDevices = scan.advertisements.values.toList()))
        assertEquals(listOf("find:AA:01", "find:BB:02"), candidates.map { it.id })
        assertTrue(candidates[0].name != candidates[1].name)
    }
}
