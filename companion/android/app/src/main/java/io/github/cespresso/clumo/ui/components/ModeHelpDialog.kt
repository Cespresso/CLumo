package io.github.cespresso.clumo.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.delay

private data class ModeHelpContent(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @StringRes val displayDescription: Int,
    @StringRes val mainButtonDescription: Int,
    @StringRes val subButtonDescription: Int,
)

private fun modeHelpContent(mode: Int): ModeHelpContent? = when (mode) {
    BleUuids.MODE_POMODORO -> ModeHelpContent(
        title = R.string.seg_pomodoro,
        summary = R.string.mode_help_pomodoro_summary,
        displayDescription = R.string.mode_help_pomodoro_display,
        mainButtonDescription = R.string.button_role_pomodoro_main,
        subButtonDescription = R.string.button_role_pomodoro_sub,
    )

    BleUuids.MODE_TIMER -> ModeHelpContent(
        title = R.string.seg_timer,
        summary = R.string.mode_help_timer_summary,
        displayDescription = R.string.mode_help_timer_display,
        mainButtonDescription = R.string.button_role_timer_main,
        subButtonDescription = R.string.button_role_timer_sub,
    )

    BleUuids.MODE_DISPLAY -> ModeHelpContent(
        title = R.string.seg_patterns,
        summary = R.string.mode_help_display_summary,
        displayDescription = R.string.mode_help_display_display,
        mainButtonDescription = R.string.button_role_display_main,
        subButtonDescription = R.string.button_role_display_sub,
    )

    BleUuids.MODE_VISUALIZER -> ModeHelpContent(
        title = R.string.seg_viz,
        summary = R.string.mode_help_visualizer_summary,
        displayDescription = R.string.mode_help_visualizer_display,
        mainButtonDescription = R.string.button_role_viz_main,
        subButtonDescription = R.string.button_role_viz_sub,
    )

    else -> null
}

@Composable
fun ModeHelpHeader(
    mode: Int,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = modeHelpContent(mode) ?: return
    val helpLabel = stringResource(R.string.mode_help_open, stringResource(content.title))
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(content.title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
        )
        HelpBadge(description = helpLabel, onClick = onHelpClick)
    }
}

@Composable
fun ModeHelpDialog(
    mode: Int,
    appearance: DeviceAppearance,
    onDismiss: () -> Unit,
) {
    val content = modeHelpContent(mode) ?: return
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ClumoColors.White)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeHelpPreview(mode = mode, appearance = appearance)
            Text(
                text = stringResource(content.title),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .semantics { heading() },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Text(
                text = stringResource(content.summary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, bottom = 12.dp),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                lineHeight = 21.sp,
            )
            HelpDetailRow(
                marker = "▦",
                markerBackground = ClumoColors.Panel,
                markerColor = LocalClumoAccents.current.accent,
                heading = stringResource(R.string.mode_help_device_display),
                description = stringResource(content.displayDescription),
            )
            HelpDetailRow(
                markerColor = appearance.buttonAColor.toComposeColor(),
                markerBackground = ClumoColors.Panel,
                markerBorder = ClumoColors.OutlineBorder,
                heading = stringResource(R.string.appearance_button_a),
                description = stringResource(content.mainButtonDescription),
            )
            HelpDetailRow(
                markerColor = appearance.buttonBColor.toComposeColor(),
                markerBackground = ClumoColors.Panel,
                markerBorder = ClumoColors.OutlineBorder,
                heading = stringResource(R.string.appearance_button_b),
                description = stringResource(content.subButtonDescription),
            )
            CtaPillButton(
                text = stringResource(R.string.dialog_acknowledge),
                onClick = onDismiss,
                fontSize = 14.sp,
                verticalPadding = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun HelpDetailRow(
    markerColor: Color,
    markerBackground: Color,
    heading: String,
    description: String,
    marker: String? = null,
    markerBorder: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = 1.dp,
                color = ClumoColors.Divider,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(markerBackground)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (marker != null) {
                Text(
                    text = marker,
                    color = markerColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(markerColor)
                        .then(
                            if (markerBorder != null) {
                                Modifier.border(1.5.dp, markerBorder, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = heading,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ModeHelpPreview(mode: Int, appearance: DeviceAppearance) {
    var tick by remember(mode) { mutableIntStateOf(0) }
    LaunchedEffect(mode) {
        while (true) {
            delay(DEMO_TICK_MS)
            tick = (tick + 1) % DEMO_TICK_WRAP
        }
    }
    ClumoDevice(
        bits = demoBits(mode, tick),
        size = 116.dp,
        appearance = appearance,
        glow = true,
        shadowElevation = 8.dp,
    )
}

private fun demoBits(mode: Int, tick: Int): Long = when (mode) {
    BleUuids.MODE_POMODORO -> countdownDemoBits(
        tick = tick,
        blinkTicks = POMODORO_BLINK_TICKS,
        blinkFrames = POMODORO_BLINK_FRAMES,
    )

    BleUuids.MODE_TIMER -> countdownDemoBits(
        tick = tick,
        blinkTicks = TIMER_BLINK_TICKS,
        blinkFrames = TIMER_DEMO_BLINK_FRAMES,
    )

    BleUuids.MODE_DISPLAY -> {
        if ((tick / DISPLAY_FRAME_TICKS) % 2 == 0) HEART_BITS else SMILE_BITS
    }

    BleUuids.MODE_VISUALIZER -> {
        FaceBits.fromColumns(VISUALIZER_FRAMES[(tick / VISUALIZER_FRAME_TICKS) % VISUALIZER_FRAMES.size])
    }

    else -> FaceBits.EMPTY
}

private fun countdownDemoBits(tick: Int, blinkTicks: Int, blinkFrames: Int): Long {
    val completionTicks = blinkTicks * blinkFrames
    val cycleTicks = COUNTDOWN_TICKS + completionTicks
    val cycleTick = tick % cycleTicks
    if (cycleTick < COUNTDOWN_TICKS) {
        return -1L shl cycleTick
    }
    val completionTick = cycleTick - COUNTDOWN_TICKS
    return if ((completionTick / blinkTicks) % 2 == 0) -1L else FaceBits.EMPTY
}

private const val DEMO_TICK_MS = 50L
// Divisible by all four demo cycles (88, 96, 48, and 64 ticks).
private const val DEMO_TICK_WRAP = 10_560
private const val COUNTDOWN_TICKS = 64
private const val POMODORO_BLINK_TICKS = 4 // 4 * 50 ms = firmware's 200 ms.
private const val TIMER_BLINK_TICKS = 8 // 8 * 50 ms = firmware's 400 ms.
private const val POMODORO_BLINK_FRAMES = 6
private const val TIMER_DEMO_BLINK_FRAMES = 4
private const val DISPLAY_FRAME_TICKS = 24
private const val VISUALIZER_FRAME_TICKS = 8

private val HEART_BITS = FaceBits.fromBitsString(
    listOf(
        "00000000",
        "01100110",
        "11111111",
        "11111111",
        "01111110",
        "00111100",
        "00011000",
        "00000000",
    ).joinToString(separator = ""),
)

private val SMILE_BITS = FaceBits.fromBitsString(
    listOf(
        "00000000",
        "01100110",
        "01100110",
        "00000000",
        "10000001",
        "01000010",
        "00111100",
        "00000000",
    ).joinToString(separator = ""),
)

private val VISUALIZER_FRAMES = listOf(
    intArrayOf(1, 2, 1, 3, 2, 1, 2, 1),
    intArrayOf(2, 4, 3, 6, 5, 3, 4, 2),
    intArrayOf(5, 7, 4, 8, 6, 5, 7, 3),
    intArrayOf(3, 5, 2, 6, 8, 4, 5, 2),
    intArrayOf(1, 3, 5, 4, 2, 7, 3, 1),
    intArrayOf(4, 6, 8, 5, 7, 3, 6, 4),
    intArrayOf(2, 3, 2, 4, 3, 2, 3, 2),
    intArrayOf(1, 2, 1, 2, 1, 2, 1, 2),
)
