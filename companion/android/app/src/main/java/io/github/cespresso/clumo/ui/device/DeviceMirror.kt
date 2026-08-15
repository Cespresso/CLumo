package io.github.cespresso.clumo.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.FaceBits
import kotlinx.coroutines.delay

/** Collects what the mirror needs from a live link and applies [mirrorBitsFor]. */
@Composable
fun liveMirrorBits(
    connection: DeviceConnection?,
    selectedPatternBits: String?,
): Long {
    if (connection == null) return FaceBits.EMPTY
    val state by connection.connectionState.collectAsState()
    val mode by connection.currentMode.collectAsState()
    val pomodoro by connection.pomodoroStatus.collectAsState()
    val timer by connection.timerStatus.collectAsState()
    val timerBlinkOn = completionBlink(
        mode == BleUuids.MODE_TIMER && timer?.isCompleted == true
    )
    val columns by connection.audioVisualizer.columns.collectAsState()
    val vizActive by connection.audioVisualizer.isActive.collectAsState()
    if (state != ConnectionState.Ready) return FaceBits.EMPTY
    return mirrorBitsFor(
        mode = mode,
        pomodoro = pomodoro,
        timer = timer,
        selectedPatternBits = selectedPatternBits,
        columns = columns,
        visualizerActive = vizActive,
        timerBlinkOn = timerBlinkOn,
    )
}

@Composable
internal fun completionBlink(active: Boolean): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        visible = true
        while (active) {
            delay(400)
            visible = !visible
        }
    }
    return visible
}
