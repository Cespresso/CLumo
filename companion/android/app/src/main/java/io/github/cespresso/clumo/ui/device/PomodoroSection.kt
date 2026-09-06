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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

@Composable
internal fun PomodoroSection(
    status: PomodoroStatus,
    onDurationsChange: (Int, Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    var workMin by remember { mutableIntStateOf(status.workMin.coerceIn(1, 99)) }
    var breakMin by remember { mutableIntStateOf(status.breakMin.coerceIn(1, 99)) }
    LaunchedEffect(status.workMin, status.breakMin) {
        workMin = status.workMin.coerceIn(1, 99)
        breakMin = status.breakMin.coerceIn(1, 99)
    }

    fun pushDurations() {
        onDurationsChange(workMin, breakMin)
    }

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
        // Phase chip
        val work = status.isWorkPhase
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (work) accents.accent else ClumoColors.BreakChipBg)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stringResource(
                    if (work) R.string.pomodoro_phase_work else R.string.pomodoro_phase_break,
                ),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = if (work) accents.onAccent else ClumoColors.BreakChipFg,
            )
        }

        // Remaining time
        Text(
            text = status.formatRemaining(),
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
        )

        // Duration steppers (visible while idle)
        if (status.isIdle) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DurationStepperRow(
                    label = stringResource(R.string.pomodoro_work_label),
                    value = workMin,
                    onChange = {
                        workMin = it
                        pushDurations()
                    },
                )
                DurationStepperRow(
                    label = stringResource(R.string.pomodoro_break_label),
                    value = breakMin,
                    onChange = {
                        breakMin = it
                        pushDurations()
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CtaPillButton(
                text = stringResource(
                    if (status.isRunning) R.string.pomodoro_pause else R.string.pomodoro_start,
                ),
                onClick = {
                    if (status.isRunning) onPause() else onStart()
                },
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1.4f),
            )
            OutlinePillButton(
                text = stringResource(R.string.pomodoro_reset),
                onClick = onReset,
                fontSize = 15.sp,
                verticalPadding = 14.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DurationStepperRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
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
        StepperButton(text = "−", enabled = value > 1) { onChange((value - 1).coerceIn(1, 99)) }
        Text(
            text = "$value",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepperButton(text = "＋", enabled = value < 99) { onChange((value + 1).coerceIn(1, 99)) }
        Text(
            text = stringResource(R.string.pomodoro_minutes_unit),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
