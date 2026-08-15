package io.github.cespresso.clumo.domain

/**
 * What the device is currently doing. The numbers are the firmware's own, written to and
 * reported by the MODE characteristic, and [ORDER] is the order the device cycles them in,
 * which is also the order the app's selector shows.
 *
 * A mode is carried as a nullable Int rather than an enum because "the device has not said
 * yet" is a state every caller has to draw, and it is not one of these four.
 */
object DeviceMode {
    const val POMODORO = 0
    const val TIMER = 1
    const val DISPLAY = 2
    const val VISUALIZER = 3

    val ORDER = listOf(POMODORO, TIMER, DISPLAY, VISUALIZER)
}
