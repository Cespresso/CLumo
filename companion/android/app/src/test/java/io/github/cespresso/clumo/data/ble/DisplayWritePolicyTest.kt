package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayWritePolicyTest {
    @Test
    fun onlyNoResponseDisplayStreamsMayBeCoalesced() {
        assertTrue(displayWriteMayBeCoalesced(noResponse = true))
        assertFalse(displayWriteMayBeCoalesced(noResponse = false))
    }
}
