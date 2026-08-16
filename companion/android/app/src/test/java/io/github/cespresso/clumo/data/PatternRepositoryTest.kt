package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternRepositoryTest {

    @Test
    fun saveAndApplyChangesOnlyTheTargetDevice() {
        val existing = Pattern(id = "existing", name = "Existing", bits = "0".repeat(64))
        val saved = Pattern(id = "saved", name = "Saved", bits = "1".repeat(64))

        val update = saveAndApplyPattern(
            patterns = listOf(existing),
            appliedPatternIds = mapOf("other-device" to existing.id),
            deviceId = "target-device",
            pattern = saved,
        )

        assertEquals(listOf(existing, saved), update.patterns)
        assertEquals(
            mapOf("other-device" to existing.id, "target-device" to saved.id),
            update.appliedPatternIds,
        )
    }

    @Test
    fun deletingPatternRemovesEveryAppliedReferenceWithoutClaimingAnotherFrame() {
        assertEquals(
            mapOf("kept-device" to "kept"),
            appliedPatternIdsAfterRemoval(
                appliedPatternIds = mapOf(
                    "first-device" to "deleted",
                    "second-device" to "deleted",
                    "kept-device" to "kept",
                ),
                removedPatternId = "deleted",
            ),
        )
    }

    @Test
    fun legacySelectionMigratesToPrimaryWithoutOverwritingExistingApplication() {
        assertEquals(
            mapOf("secondary" to "two", "primary" to "one"),
            migrateLegacyPatternSelection(
                legacyPatternId = "one",
                appliedPatternIds = mapOf("secondary" to "two"),
                primaryDeviceId = "primary",
                knownDeviceIds = listOf("secondary", "primary"),
            ),
        )
        assertEquals(
            mapOf("primary" to "newer"),
            migrateLegacyPatternSelection(
                legacyPatternId = "old",
                appliedPatternIds = mapOf("primary" to "newer"),
                primaryDeviceId = "primary",
                knownDeviceIds = listOf("primary"),
            ),
        )
    }

    @Test
    fun legacySelectionIsRetainedUntilThereIsAMigrationTarget() {
        assertFalse(legacySelectionCanBeConsumed(null, emptyList()))
        assertTrue(legacySelectionCanBeConsumed(null, listOf("known")))
        assertTrue(legacySelectionCanBeConsumed("primary", emptyList()))
    }

    @Test
    fun appliedPatternMapRoundTripsJsonForMultipleDevices() {
        val original = linkedMapOf("device-a" to "pattern-1", "device-b" to "pattern-2")

        assertEquals(original, decodeAppliedPatternIds(encodeAppliedPatternIds(original)))
    }

    @Test
    fun upsertPatternAppendsANewPattern() {
        val existing = Pattern(id = "existing", name = "Existing", bits = "0".repeat(64))
        val added = Pattern(id = "added", name = "Added", bits = "1".repeat(64))

        assertEquals(listOf(existing, added), upsertPattern(listOf(existing), added))
    }

    @Test
    fun upsertPatternReplacesAnExistingPatternWithoutChangingItsPosition() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val updatedFirst = first.copy(name = "Updated", bits = "1".repeat(64))

        assertEquals(
            listOf(updatedFirst, second),
            upsertPattern(listOf(first, second), updatedFirst),
        )
    }

    @Test
    fun cyclePatternMovesForwardAndWrapsAround() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val third = Pattern(id = "third", name = "Third", bits = "0".repeat(64))
        val all = listOf(first, second, third)

        assertEquals(second, cyclePattern(all, "first", forward = true))
        assertEquals(first, cyclePattern(all, "third", forward = true))
    }

    @Test
    fun cyclePatternMovesBackwardAndWrapsAround() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val third = Pattern(id = "third", name = "Third", bits = "0".repeat(64))
        val all = listOf(first, second, third)

        assertEquals(second, cyclePattern(all, "third", forward = false))
        assertEquals(third, cyclePattern(all, "first", forward = false))
    }

    @Test
    fun cyclePatternFallsBackToTheFirstPatternWhenTheSelectionIsUnknown() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val all = listOf(first, second)

        assertEquals(first, cyclePattern(all, null, forward = true))
        assertEquals(first, cyclePattern(all, "deleted", forward = false))
    }

    @Test
    fun cyclePatternReturnsTheOnlyPatternUnchanged() {
        val only = Pattern(id = "only", name = "Only", bits = "0".repeat(64))

        assertEquals(only, cyclePattern(listOf(only), "only", forward = true))
        assertEquals(only, cyclePattern(listOf(only), "only", forward = false))
    }

    @Test
    fun cyclePatternReturnsNullWhenThereAreNoPatterns() {
        assertNull(cyclePattern(emptyList(), null, forward = true))
    }
}
