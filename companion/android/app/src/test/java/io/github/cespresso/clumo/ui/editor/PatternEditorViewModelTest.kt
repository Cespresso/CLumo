package io.github.cespresso.clumo.ui.editor

import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test

class PatternEditorViewModelTest {

    @Test
    fun saveKeepsExistingIdAndUsesEditedCells() {
        val existing = Pattern("existing", "Old", "0".repeat(64))
        val cells = 1L shl 17

        assertEquals(
            Pattern("existing", "Renamed", FaceBits.toBitsString(cells)),
            editorPattern(existing, "Renamed", cells, newId = "unused"),
        )
    }

    @Test
    fun newPatternUsesSuppliedStableId() {
        assertEquals(
            "generated",
            editorPattern(null, "New", FaceBits.EMPTY, newId = "generated").id,
        )
    }
}
