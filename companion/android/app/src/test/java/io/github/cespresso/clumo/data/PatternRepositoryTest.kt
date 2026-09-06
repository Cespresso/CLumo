package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternRepositoryTest {

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
    fun saveAndSelectPatternSelectsThePatternInTheSameUpdate() {
        val existing = Pattern(id = "existing", name = "Existing", bits = "0".repeat(64))
        val saved = Pattern(id = "saved", name = "Saved", bits = "1".repeat(64))

        val update = saveAndSelectPattern(listOf(existing), saved)

        assertEquals(listOf(existing, saved), update.patterns)
        assertEquals(saved.id, update.selectedId)
    }

    @Test
    fun cyclePatternIdMovesForwardAndWrapsAround() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val third = Pattern(id = "third", name = "Third", bits = "0".repeat(64))
        val all = listOf(first, second, third)

        assertEquals("second", cyclePatternId(all, "first", forward = true))
        assertEquals("first", cyclePatternId(all, "third", forward = true))
    }

    @Test
    fun cyclePatternIdMovesBackwardAndWrapsAround() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val third = Pattern(id = "third", name = "Third", bits = "0".repeat(64))
        val all = listOf(first, second, third)

        assertEquals("second", cyclePatternId(all, "third", forward = false))
        assertEquals("third", cyclePatternId(all, "first", forward = false))
    }

    @Test
    fun cyclePatternIdFallsBackToTheFirstPatternWhenTheSelectionIsUnknown() {
        val first = Pattern(id = "first", name = "First", bits = "0".repeat(64))
        val second = Pattern(id = "second", name = "Second", bits = "0".repeat(64))
        val all = listOf(first, second)

        assertEquals("first", cyclePatternId(all, null, forward = true))
        assertEquals("first", cyclePatternId(all, "deleted", forward = false))
    }

    @Test
    fun cyclePatternIdReturnsTheOnlyPatternUnchanged() {
        val only = Pattern(id = "only", name = "Only", bits = "0".repeat(64))

        assertEquals("only", cyclePatternId(listOf(only), "only", forward = true))
        assertEquals("only", cyclePatternId(listOf(only), "only", forward = false))
    }

    @Test
    fun cyclePatternIdReturnsNullWhenThereAreNoPatterns() {
        assertNull(cyclePatternId(emptyList(), null, forward = true))
    }
}
