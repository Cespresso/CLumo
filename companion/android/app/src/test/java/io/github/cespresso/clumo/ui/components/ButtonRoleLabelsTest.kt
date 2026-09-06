package io.github.cespresso.clumo.ui.components

import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.BleUuids
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonRoleLabelsTest {

    @Test
    fun mapsEachModeToItsRoleStrings() {
        assertEquals(
            ButtonRoleLabels(R.string.button_role_pomodoro_main, R.string.button_role_pomodoro_sub),
            buttonRoleLabels(BleUuids.MODE_POMODORO),
        )
        assertEquals(
            ButtonRoleLabels(R.string.button_role_timer_main, R.string.button_role_timer_sub),
            buttonRoleLabels(BleUuids.MODE_TIMER),
        )
        assertEquals(
            ButtonRoleLabels(R.string.button_role_display_main, R.string.button_role_display_sub),
            buttonRoleLabels(BleUuids.MODE_DISPLAY),
        )
        assertEquals(
            ButtonRoleLabels(R.string.button_role_viz_main, R.string.button_role_viz_sub),
            buttonRoleLabels(BleUuids.MODE_VISUALIZER),
        )
    }

    @Test
    fun fallsBackToPomodoroForUnknownModes() {
        assertEquals(buttonRoleLabels(BleUuids.MODE_POMODORO), buttonRoleLabels(-1))
        assertEquals(buttonRoleLabels(BleUuids.MODE_POMODORO), buttonRoleLabels(99))
    }
}
