package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

@Composable
internal fun CountdownTimerSection(
    status: CountdownTimerStatus,
    completionBlinkOn: Boolean,
    onDurationChanged: (Int, Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(status.configuredMin.coerceIn(0, 59)) }
    var seconds by remember { mutableIntStateOf(status.configuredSec.coerceIn(0, 59)) }
    LaunchedEffect(status.configuredMin, status.configuredSec) {
        minutes = status.configuredMin.coerceIn(0, 59)
        seconds = status.configuredSec.coerceIn(0, 59)
    }

    val stateLabel = stringResource(
        when {
            status.isRunning -> R.string.timer_state_running
            status.isPaused -> R.string.timer_state_paused
            status.isCompleted -> R.string.timer_state_completed
            else -> R.string.timer_state_idle
        }
    )
    val primaryLabel = stringResource(
        when {
            status.isRunning -> R.string.timer_pause
            status.isPaused -> R.string.timer_resume
            else -> R.string.timer_start
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (status.isCompleted) ClumoColors.Coral else accents.accent)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stateLabel,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = if (status.isCompleted) ClumoColors.White else accents.onAccent,
            )
        }

        Text(
            text = status.formatRemaining(),
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.alpha(
                if (status.isCompleted && !completionBlinkOn) 0.12f else 1f
            ),
        )

        if (status.isIdle) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimerStepperRow(
                    label = stringResource(R.string.timer_minutes_label),
                    value = minutes,
                    decrementEnabled = minutes > 0 && !(minutes == 1 && seconds == 0),
                    incrementEnabled = minutes < 59,
                    onDecrement = {
                        val next = minutes - 1
                        minutes = next
                        onDurationChanged(next, seconds)
                    },
                    onIncrement = {
                        val next = minutes + 1
                        minutes = next
                        onDurationChanged(next, seconds)
                    },
                )
                TimerStepperRow(
                    label = stringResource(R.string.timer_seconds_label),
                    value = seconds,
                    decrementEnabled = seconds > 0 && !(minutes == 0 && seconds == 1),
                    incrementEnabled = seconds < 59,
                    onDecrement = {
                        val next = seconds - 1
                        seconds = next
                        onDurationChanged(minutes, next)
                    },
                    onIncrement = {
                        val next = seconds + 1
                        seconds = next
                        onDurationChanged(minutes, next)
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CtaPillButton(
                text = primaryLabel,
                onClick = {
                    if (status.isRunning) onPause() else onStart()
                },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1.4f),
            )
            OutlinePillButton(
                text = stringResource(R.string.timer_cancel),
                onClick = onCancel,
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimerStepperRow(
    label: String,
    value: Int,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier.weight(1f),
        )
        StepperButton(text = "−", enabled = decrementEnabled, onClick = onDecrement)
        Text(
            text = "%02d".format(value),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(54.dp),
        )
        StepperButton(text = "＋", enabled = incrementEnabled, onClick = onIncrement)
    }
}
