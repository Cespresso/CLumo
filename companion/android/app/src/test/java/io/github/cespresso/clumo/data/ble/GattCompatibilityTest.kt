package io.github.cespresso.clumo.data.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class GattCompatibilityTest {
    private val required = setOf(
        UUID.fromString("681285a6-247f-48c6-80ad-68c3dce18587"),
        UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858a"),
    )
    private val button = UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858b")
    private val optional = setOf(button)

    @Test
    fun refreshesStaleGattCacheOnceBeforeRejectingDevice() {
        val legacyCache = required - UUID.fromString("681285a6-247f-48c6-80ad-68c3dce1858a")

        assertEquals(
            GattCompatibilityAction.REFRESH_CACHE,
            gattCompatibilityAction(legacyCache, required, optional, cacheRefreshAttempted = false),
        )
        assertEquals(
            GattCompatibilityAction.REJECT,
            gattCompatibilityAction(legacyCache, required, optional, cacheRefreshAttempted = true),
        )
    }

    @Test
    fun acceptsCompleteGattDatabaseWithoutRefresh() {
        assertEquals(
            GattCompatibilityAction.ACCEPT,
            gattCompatibilityAction(required + button, required, optional, cacheRefreshAttempted = false),
        )
    }

    @Test
    fun refreshesEmptyGattDatabaseBeforeRejectingDevice() {
        assertEquals(
            GattCompatibilityAction.REFRESH_CACHE,
            gattCompatibilityAction(emptySet(), required, optional, cacheRefreshAttempted = false),
        )
        assertEquals(
            GattCompatibilityAction.REJECT,
            gattCompatibilityAction(emptySet(), required, optional, cacheRefreshAttempted = true),
        )
    }

    @Test
    fun refreshesOnceWhenOnlyAnOptionalCharacteristicIsMissing() {
        assertEquals(
            GattCompatibilityAction.REFRESH_CACHE,
            gattCompatibilityAction(required, required, optional, cacheRefreshAttempted = false),
        )
    }

    @Test
    fun acceptsMissingOptionalCharacteristicAfterRefreshingOnce() {
        assertEquals(
            GattCompatibilityAction.ACCEPT,
            gattCompatibilityAction(required, required, optional, cacheRefreshAttempted = true),
        )
    }

    @Test
    fun missingRequiredCharacteristicOutranksMissingOptionalOne() {
        assertEquals(
            GattCompatibilityAction.REJECT,
            gattCompatibilityAction(emptySet(), required, optional, cacheRefreshAttempted = true),
        )
    }

    @Test
    fun acceptsWhenThereAreNoOptionalCharacteristicsToLookFor() {
        assertEquals(
            GattCompatibilityAction.ACCEPT,
            gattCompatibilityAction(required, required, emptySet(), cacheRefreshAttempted = false),
        )
    }
}
