package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamingTest {

    @Test
    fun aNameThatIsNotCLumosOwnIsDropped() {
        assertEquals("CLumo-4E6F", DeviceNaming.ownName("CLumo-4E6F"))
        assertNull(DeviceNaming.ownName("nimble"))
        assertNull(DeviceNaming.ownName(null))
    }

    @Test
    fun theNameTheUserGaveItWins() {
        assertEquals(
            "Desk",
            DeviceNaming.displayName("id", mapOf("id" to "Desk"), scannedName = "CLumo-561C"),
        )
    }

    @Test
    fun withoutOneTheDeviceIsCalledWhatItAdvertises() {
        assertEquals(
            "CLumo-561C",
            DeviceNaming.displayName("id", emptyMap(), scannedName = "CLumo-561C", fallbackName = "stored"),
        )
    }

    @Test
    fun theStoredNameStandsInWhileNothingIsScanned() {
        assertEquals("stored", DeviceNaming.displayName("id", emptyMap(), fallbackName = "stored"))
    }

    @Test
    fun aDeviceThatHasSaidNothingYetIsStillCalledSomething() {
        // Every caller reaches this before the link reports an id, and a blank line on the
        // screen would read as a broken row rather than a device still introducing itself.
        assertEquals("CLumo", DeviceNaming.displayName(null, emptyMap()))
    }

    @Test
    fun anAliasIsOnlyReachableThroughAnId() {
        assertEquals(
            "CLumo-561C",
            DeviceNaming.displayName(null, mapOf("id" to "Desk"), scannedName = "CLumo-561C"),
        )
    }
}
