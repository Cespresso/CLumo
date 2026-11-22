package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.ui.components.product.MatrixPage
import kotlin.math.roundToInt

private fun modeName(mode: Int) =
    when (mode) {
        DeviceMode.POMODORO -> "Pomodoro"
        DeviceMode.TIMER -> "Timer"
        DeviceMode.DISPLAY -> "Patterns"
        DeviceMode.VISUALIZER -> "Visualizer"
        else -> "Mode"
    }

private fun DeviceUiState.modeSummary() =
    when (effectiveMode) {
        DeviceMode.POMODORO -> pomodoroStatus.run {
            (if (isWorkPhase) "Focus" else "Break") + " · " + formatRemaining()
        }
        DeviceMode.TIMER -> timerStatus.formatRemaining()
        DeviceMode.DISPLAY -> patterns.firstOrNull { it.id == shownPatternId }?.name ?: "Custom pattern"
        DeviceMode.VISUALIZER -> if (visualizerActive) "Active" else "Standby"
        else -> "—"
    }

private val buttons = listOf(MatrixPage.MainButton, MatrixPage.SubButton)
private fun buttonName(page: MatrixPage) = if (page == MatrixPage.MainButton) "Main button" else "Sub button"

private fun DeviceUiState.buttonAction(page: MatrixPage): String {
    if (!ready) return "—"
    val main = page == MatrixPage.MainButton
    return when (effectiveMode) {
        DeviceMode.POMODORO -> when {
            !main -> "Reset"
            pomodoroStatus.isIdle -> "Start"
            pomodoroStatus.isRunning -> "Pause"
            else -> "Resume"
        }
        DeviceMode.TIMER -> when {
            !main -> "Cancel"
            timerStatus.isPaused -> "Resume"
            timerStatus.isRunning -> "Pause"
            timerStatus.isCompleted -> "Restart"
            else -> "Start"
        }
        DeviceMode.DISPLAY -> if (main) "Next pattern" else "Previous pattern"
        DeviceMode.VISUALIZER -> if (main) "Sensitivity up" else "Sensitivity down"
        else -> "—"
    }
}

@Composable
internal fun MatrixOverview(ui: DeviceUiState, open: (MatrixPage) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(role = Role.Button) { open(MatrixPage.Mode) }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (ui.ready) modeName(ui.effectiveMode) else "Mode", style = MaterialTheme.typography.bodyMedium)
                Text(if (ui.ready) ui.modeSummary() else "—", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("›")
            }
            TextButton(onClick = { open(MatrixPage.Display) }) { Text("LED settings", color = ClumoColors.Text) }
        }
        HorizontalDivider(color = ClumoColors.Text.copy(alpha = .18f))
        Row {
            buttons.forEach { page ->
                Column(
                    Modifier.weight(1f).clickable(role = Role.Button) { open(page) }.heightIn(min = 56.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(buttonName(page), style = MaterialTheme.typography.labelMedium)
                    Text(ui.buttonAction(page), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
internal fun MatrixButtonInfo(ui: DeviceUiState, page: MatrixPage) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(buttonName(page), style = MaterialTheme.typography.titleLarge)
        Text(if (ui.ready) modeName(ui.effectiveMode) + " · Single press" else "Single press")
        Text(ui.buttonAction(page), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun MatrixDisplaySettings(
    brightness: Float,
    mode: Int,
    ready: Boolean,
    onBrightness: (Float) -> Unit,
    onMode: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("LED settings", style = MaterialTheme.typography.titleLarge)
        val level = Brightness.toLevel(brightness)
        Text("Brightness · " + (level + 1) + " / 16")
        Slider(
            value = level.toFloat(),
            onValueChange = { onBrightness(Brightness.toPercent(it.roundToInt())) },
            enabled = ready,
            valueRange = 0f..15f,
            steps = 14,
        )
        Text("Mode", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            DeviceMode.ORDER.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = mode == option,
                        enabled = ready,
                        role = Role.RadioButton,
                        onClick = { if (mode != option) onMode(option) },
                    )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(modeName(option))
                    RadioButton(selected = mode == option, onClick = null, enabled = ready)
                }
            }
        }
    }
}
