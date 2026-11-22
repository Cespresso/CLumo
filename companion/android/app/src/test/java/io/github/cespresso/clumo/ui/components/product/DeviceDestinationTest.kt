package io.github.cespresso.clumo.ui.components.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDestinationTest {
    @Test fun backConsumesExactlyOneLevel() {
        val state = FloorSettingsState(floorUi = false)
        state.selectPart(StandPart.Knob)
        state.selectBinding(2)
        assertTrue(state.navigateBack())
        assertEquals(DeviceDestination.Part(StandPart.Knob), state.destination)
        assertTrue(state.navigateBack())
        assertEquals(DeviceDestination.Detail, state.destination)
        assertFalse(state.navigateBack())
    }

    @Test fun connectionSnapshotCannotChangeDestination() {
        val state = FloorSettingsState(false)
        state.selectPart(StandPart.Knob)
        state.selectBinding(5)
        state.snapshot = FloorSettingsSnapshot(enabled = false, status = "Reconnecting")
        state.snapshot = FloorSettingsSnapshot(enabled = true)
        assertEquals(DeviceDestination.Assignment(StandPart.Knob, 5), state.destination)
    }

    @Test fun repeatedAnimatedBackAndStaleCompletionCannotSkipParent() {
        val state = FloorSettingsState(true)
        state.selectPart(StandPart.Knob)
        state.selectBinding(2)
        state.navigateBack()
        val token = state.assignmentCloseToken!!
        state.navigateBack()
        state.finishAssignmentClose(token)
        state.finishAssignmentClose(token)
        assertEquals(DeviceDestination.Part(StandPart.Knob), state.destination)
        state.navigateBack()
        val partToken = state.closeToken!!
        state.selectPart(StandPart.LeftButton)
        state.finishClose(partToken)
        assertEquals(DeviceDestination.Part(StandPart.LeftButton), state.destination)
    }

    @Test fun invalidBindingCannotCreateInvalidDestination() {
        val state = FloorSettingsState(false)
        state.selectPart(StandPart.LeftButton)
        state.selectBinding(5)
        assertEquals(DeviceDestination.Part(StandPart.LeftButton), state.destination)
    }
}
