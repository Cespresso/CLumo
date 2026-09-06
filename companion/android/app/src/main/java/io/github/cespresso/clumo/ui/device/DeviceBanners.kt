package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.BackgroundCountdown
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.PomodoroStatus
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

/** Shown while the system pairing prompt is up, so the wait does not read as a hang. */
@Composable
internal fun PairingHintBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "i",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = LocalClumoAccents.current.accent,
        )
        Text(
            text = stringResource(R.string.connection_pairing_hint),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
        )
    }
}

/**
 * Automatic reconnect progress and terminal connection failures. [action] is null while a
 * reconnect is still running: there is nothing to offer until the attempts give up.
 */
@Composable
internal fun ConnectionTroubleBanner(
    reconnecting: Boolean,
    reconnectAttempt: Int,
    failureMessage: String,
    action: DeviceFailureAction?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ClumoColors.ErrorBg)
            .border(1.5.dp, ClumoColors.ErrorBorder, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ClumoColors.Coral),
        )
        Text(
            text = if (reconnecting) {
                stringResource(R.string.connection_reconnecting_banner, reconnectAttempt)
            } else {
                failureMessage
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.ErrorText,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            val accents = LocalClumoAccents.current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accents.cta)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = action.label(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedFontFamily,
                    color = accents.onCta,
                )
            }
        }
    }
}

@Composable
private fun BackgroundCountdownBanner(message: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(LocalClumoAccents.current.accent),
        )
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.weight(1f),
        )
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accents.cta)
                .clickable(onClick = onAction)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = accents.onCta,
            )
        }
    }
}

@Composable
internal fun BackgroundCountdownBanner(
    countdown: BackgroundCountdown,
    pomodoro: PomodoroStatus,
    timer: CountdownTimerStatus,
    onPomodoroReset: () -> Unit,
    onTimerCancel: () -> Unit,
) {
    val pomodoroBanner = countdown == BackgroundCountdown.PomodoroRunning ||
        countdown == BackgroundCountdown.PomodoroPaused
    BackgroundCountdownBanner(
        message = when (countdown) {
            BackgroundCountdown.PomodoroRunning ->
                stringResource(R.string.pomodoro_running_elsewhere, pomodoro.formatRemaining())
            BackgroundCountdown.PomodoroPaused ->
                stringResource(R.string.pomodoro_paused_elsewhere, pomodoro.formatRemaining())
            BackgroundCountdown.TimerRunning ->
                stringResource(R.string.timer_running_elsewhere, timer.formatRemaining())
            BackgroundCountdown.TimerPaused ->
                stringResource(R.string.timer_paused_elsewhere, timer.formatRemaining())
            BackgroundCountdown.TimerCompleted ->
                stringResource(R.string.timer_completed_elsewhere)
        },
        actionLabel = if (pomodoroBanner) {
            stringResource(R.string.pomodoro_reset)
        } else {
            stringResource(R.string.timer_cancel)
        },
        onAction = if (pomodoroBanner) onPomodoroReset else onTimerCancel,
    )
}
