package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceJsonTest {
    @Test fun oldSavedDeviceDefaultsToClumo() {
        val device = decodeDevices("""[{"id":"one","address":"AA","lastSeenAt":1}]""").single()
        assertEquals(DeviceKind.CLUMO, device.kind)
    }

    @Test fun newKindSurvivesPersistence() {
        val device = Device("one", "AA", "Stand", 1, DeviceKind.HEADPHONE_STAND)
        assertEquals(listOf(device), decodeDevices(encodeDevices(listOf(device))))
    }
}
