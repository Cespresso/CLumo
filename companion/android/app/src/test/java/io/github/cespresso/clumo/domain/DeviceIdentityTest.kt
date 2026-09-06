package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdentityTest {
    private val a = Device(id = "id-a", address = "AA:AA", name = "CLumo-AAAA", lastSeenAt = 10)
    private val b = Device(id = "id-b", address = "BB:BB", name = "CLumo-BBBB", lastSeenAt = 20)

    @Test
    fun aResetDeviceReplacesTheRecordWithItsOldId() {
        val reset = a.copy(id = "id-a2", lastSeenAt = 30)
        assertEquals(listOf(reset, b), mergeKnownDevice(listOf(a, b), reset))
    }

    @Test
    fun aDeviceSeenAtANewAddressReplacesTheRecordWithItsOldAddress() {
        val moved = a.copy(address = "CC:CC", lastSeenAt = 30)
        assertEquals(listOf(moved, b), mergeKnownDevice(listOf(a, b), moved))
    }

    @Test
    fun anUnrelatedDeviceIsAdded() {
        val c = Device(id = "id-c", address = "CC:CC", name = null, lastSeenAt = 5)
        assertEquals(listOf(b, a, c), mergeKnownDevice(listOf(a, b), c))
    }

    @Test
    fun aSettingFollowsTheDeviceToItsNewId() {
        assertEquals(mapOf("id-a2" to "Desk", "id-b" to "Shelf"), moveDeviceKey(mapOf("id-a" to "Desk", "id-b" to "Shelf"), "id-a", "id-a2"))
    }

    @Test
    fun aSettingAlreadyMadeUnderTheNewIdIsKept() {
        assertEquals(mapOf("id-a2" to "New"), moveDeviceKey(mapOf("id-a" to "Old", "id-a2" to "New"), "id-a", "id-a2"))
    }

    @Test
    fun nothingToMoveLeavesTheSettingsAlone() {
        val settings = mapOf("id-b" to "Shelf")
        assertEquals(settings, moveDeviceKey(settings, "id-a", "id-a2"))
    }
}
