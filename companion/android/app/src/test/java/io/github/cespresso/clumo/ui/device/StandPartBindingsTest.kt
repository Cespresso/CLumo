package io.github.cespresso.clumo.ui.device

import io.github.cespresso.clumo.data.ble.ControlBindings
import io.github.cespresso.clumo.ui.components.product.StandPart
import org.junit.Assert.assertEquals
import org.junit.Test

class StandPartBindingsTest {
    @Test
    fun changingOnePartPreservesEveryOtherGesture() {
        for (part in StandPart.entries) {
            for (index in part.bindingIndices) {
                val original = ControlBindings.DEFAULT
                val changed = changePartBinding(original, part, index, 0)
                for (field in 0..5) assertEquals(if (field == index) 0 else original.value(field), changed.value(field))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun buttonCannotWriteKnobBinding() {
        changePartBinding(ControlBindings.DEFAULT, StandPart.LeftButton, 5, 0)
    }

    @Test
    fun ledAndSurfaceResolveToTheSamePart() {
        assertEquals(StandPart.Matrix, StandPart.fromNode("led_matrix"))
        assertEquals(StandPart.Matrix, StandPart.fromNode("matrix_surface"))
        assertEquals(null, StandPart.fromNode("button_b"))
        assertEquals(StandPart.Knob, StandPart.fromNode("stand_knob"))
    }
}
