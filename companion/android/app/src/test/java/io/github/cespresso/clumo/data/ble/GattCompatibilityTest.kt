package io.github.cespresso.clumo.data.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class GattCompatibilityTest {
    private val required = setOf(
        UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18587"),
        UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858a"),
    )

    @Test
    fun refreshesStaleGattCacheOnceBeforeRejectingDevice() {
        val legacyCache = required - UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858a")

        assertEquals(
            GattCompatibilityAction.REFRESH_CACHE,
            gattCompatibilityAction(legacyCache, required, cacheRefreshAttempted = false),
        )
        assertEquals(
            GattCompatibilityAction.REJECT,
            gattCompatibilityAction(legacyCache, required, cacheRefreshAttempted = true),
        )
    }

    @Test
    fun acceptsCompleteGattDatabaseWithoutRefresh() {
        assertEquals(
            GattCompatibilityAction.ACCEPT,
            gattCompatibilityAction(required, required, cacheRefreshAttempted = false),
        )
    }

    @Test
    fun refreshesEmptyGattDatabaseBeforeRejectingDevice() {
        assertEquals(
            GattCompatibilityAction.REFRESH_CACHE,
            gattCompatibilityAction(emptySet(), required, cacheRefreshAttempted = false),
        )
        assertEquals(
            GattCompatibilityAction.REJECT,
            gattCompatibilityAction(emptySet(), required, cacheRefreshAttempted = true),
        )
    }
}
