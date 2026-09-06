package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryTargetTest {

    private fun device(id: String, address: String) =
        Device(id = id, address = address, name = "CLumo", lastSeenAt = 0L)

    @Test
    fun picksTheDesignatedPrimary() {
        val a = device("id-a", "AA:AA:AA:AA:AA:AA")
        val b = device("id-b", "BB:BB:BB:BB:BB:BB")
        assertEquals(b, resolvePrimaryTarget("id-b", listOf(a, b)))
    }

    @Test
    fun fallsBackToTheOnlyKnownDeviceWhenTheIdDangles() {
        val a = device("id-a", "AA:AA:AA:AA:AA:AA")
        assertEquals(a, resolvePrimaryTarget("id-gone", listOf(a)))
    }

    @Test
    fun fallsBackToTheOnlyKnownDeviceWhenNoPrimaryIsSet() {
        val a = device("id-a", "AA:AA:AA:AA:AA:AA")
        assertEquals(a, resolvePrimaryTarget(null, listOf(a)))
    }

    @Test
    fun resolvesNothingWhenNoDevicesAreKnown() {
        assertNull(resolvePrimaryTarget(null, emptyList()))
        assertNull(resolvePrimaryTarget("id-a", emptyList()))
    }

    @Test
    fun refusesToGuessAmongSeveralUndesignatedDevices() {
        val a = device("id-a", "AA:AA:AA:AA:AA:AA")
        val b = device("id-b", "BB:BB:BB:BB:BB:BB")
        assertNull(resolvePrimaryTarget(null, listOf(a, b)))
        assertNull(resolvePrimaryTarget("id-gone", listOf(a, b)))
    }
}
