package io.github.cespresso.clumo.data.ble

/**
 * A physical button press the firmware forwarded instead of handling itself.
 * [mode] is the mode the press happened in, so it stays correct even if the
 * notification arrives just after a mode change.
 */
data class ButtonEvent(val mode: Int, val button: Int) {

    val isMain: Boolean get() = button == BleUuids.BUTTON_MAIN

    companion object {
        fun parse(value: ByteArray): ButtonEvent? {
            if (value.size < 2) return null
            val mode = value[0].toInt() and 0xFF
            val button = value[1].toInt() and 0xFF
            if (mode != BleUuids.MODE_DISPLAY && mode != BleUuids.MODE_VISUALIZER) return null
            if (button != BleUuids.BUTTON_MAIN && button != BleUuids.BUTTON_SUB) return null
            return ButtonEvent(mode, button)
        }
    }
}
