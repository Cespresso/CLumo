package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.Pattern
import org.junit.Assert.assertEquals
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
}
