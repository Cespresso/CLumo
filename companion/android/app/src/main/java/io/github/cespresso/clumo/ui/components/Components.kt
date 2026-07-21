package io.github.cespresso.clumo.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.TimerStatus
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

// ---------------------------------------------------------------------------
// 8x8 face bitmask helpers (bit i = row-major cell i, row 0 = top, bit set = lit)
// ---------------------------------------------------------------------------

object FaceBits {
    const val EMPTY = 0L

    fun fromBitsString(bits: String): Long {
        var mask = 0L
        val n = minOf(bits.length, 64)
        for (i in 0 until n) {
            if (bits[i] == '1') mask = mask or (1L shl i)
        }
        return mask
    }

    /**
     * Pixel countdown, identical rule to the firmware:
     * lit = ceil(remainingSec * 64 / phaseTotalSec) clamped 0..64,
     * pixels ON for row-major index < lit.
     */
    fun fromTimer(status: TimerStatus): Long {
        val total = status.phaseTotalSec
        if (total <= 0) return EMPTY
        val lit = ((status.remainingSec.toLong() * 64 + total - 1) / total)
            .coerceIn(0, 64)
            .toInt()
        return if (lit >= 64) -1L else (1L shl lit) - 1
    }

    fun toBitsString(mask: Long): String = buildString {
        for (i in 0 until 64) append(if ((mask shr i) and 1L == 1L) '1' else '0')
    }

    /** Column heights 0..8 -> bars growing from the bottom. */
    fun fromColumns(columns: IntArray): Long {
        var mask = 0L
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val h = columns.getOrElse(col) { 0 }.coerceIn(0, 8)
                if (8 - row <= h) mask = mask or (1L shl (row * 8 + col))
            }
        }
        return mask
    }
}

// ---------------------------------------------------------------------------
// Connection-state visuals
// ---------------------------------------------------------------------------

/** Frame color for a device face; pulses gray<->sage while connecting. */
@Composable
fun connectionFrameColor(state: ConnectionState): Color {
    val connecting = state == ConnectionState.Connecting ||
        state == ConnectionState.Reconnecting ||
        state == ConnectionState.Bonding ||
        state == ConnectionState.Connected ||
        state == ConnectionState.Synchronizing
    if (connecting) {
        val transition = rememberInfiniteTransition(label = "framePulse")
        val color by transition.animateColor(
            initialValue = ClumoColors.Gray,
            targetValue = ClumoColors.Sage,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "framePulseColor",
        )
        return color
    }
    val target = when (state) {
        ConnectionState.Ready -> ClumoColors.Sage
        ConnectionState.Error -> ClumoColors.Coral
        else -> ClumoColors.Gray
    }
    val color by animateColorAsState(target, label = "frameColor")
    return color
}

@Composable
fun connectionLabel(state: ConnectionState): Pair<String, Color> = when (state) {
    ConnectionState.Ready -> stringResource(R.string.state_connected) to ClumoColors.Sage
    ConnectionState.Connecting,
    ConnectionState.Reconnecting,
    ConnectionState.Bonding,
    ConnectionState.Connected,
    ConnectionState.Synchronizing -> stringResource(R.string.state_connecting) to ClumoColors.Muted
    ConnectionState.Error -> stringResource(R.string.state_error) to ClumoColors.Coral
    ConnectionState.Disconnected -> stringResource(R.string.state_disconnected) to ClumoColors.MutedLight
}

// ---------------------------------------------------------------------------
// Brand motif: thick rounded L-corner stroke
// ---------------------------------------------------------------------------

@Composable
fun BrandCorner(
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    color: Color = ClumoColors.Sage,
) {
    Canvas(modifier = modifier.size(size)) {
        val sw = stroke.toPx()
        val half = sw / 2f
        val side = this.size.width
        // Match the launcher foreground: a quarter-size inner curve with rounded ends.
        // Keeping endpoints half a stroke inside the bounds prevents the round caps from
        // extending beyond this composable's requested size.
        val centerRadius = (side / 4f).coerceAtLeast(1f)
        val terminal = (side - half).coerceAtLeast(half)
        val path = Path().apply {
            moveTo(half, terminal)
            lineTo(half, half + centerRadius)
            arcTo(
                rect = Rect(half, half, half + centerRadius * 2, half + centerRadius * 2),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(terminal, half)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// Pill buttons
// ---------------------------------------------------------------------------

@Composable
fun CoralPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    verticalPadding: Dp = 16.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(999.dp), ambientColor = ClumoColors.Coral, spotColor = ClumoColors.Coral)
            .clip(RoundedCornerShape(999.dp))
            .background(ClumoColors.Coral)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ClumoColors.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
        )
    }
}

@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    verticalPadding: Dp = 14.dp,
    textColor: Color = ClumoColors.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.OutlineBorder, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
        )
    }
}

// ---------------------------------------------------------------------------
// Device face: colored frame + inner panel + 8x8 round dots
// ---------------------------------------------------------------------------

@Composable
fun DeviceFace(
    bits: Long,
    frameColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    frameCorner: Dp = size * 42f / 188f,
    framePadding: Dp = size * 23f / 188f,
    innerCorner: Dp = size * 21f / 188f,
    gridPadding: Dp = size * 13f / 188f,
    litAlpha: Float = 1f,
    glow: Boolean = true,
    shadowElevation: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(shadowElevation, RoundedCornerShape(frameCorner))
                } else Modifier
            )
            .size(size)
            .clip(RoundedCornerShape(frameCorner))
            .background(frameColor)
            .padding(framePadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(innerCorner))
                .background(ClumoColors.Panel)
                .padding(gridPadding),
        ) {
            DotGrid(bits = bits, litAlpha = litAlpha, glow = glow)
        }
    }
}

@Composable
fun DotGrid(
    bits: Long,
    modifier: Modifier = Modifier,
    litAlpha: Float = 1f,
    glow: Boolean = true,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cellW = this.size.width / 8f
        val cellH = this.size.height / 8f
        val dotRadius = minOf(cellW, cellH) * 0.38f
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val on = (bits shr (row * 8 + col)) and 1L == 1L
                val center = Offset(cellW * (col + 0.5f), cellH * (row + 0.5f))
                if (on) {
                    if (glow) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ClumoColors.LitDot.copy(alpha = 0.55f * litAlpha),
                                    ClumoColors.LitDot.copy(alpha = 0f),
                                ),
                                center = center,
                                radius = dotRadius * 2.1f,
                            ),
                            radius = dotRadius * 2.1f,
                            center = center,
                        )
                    }
                    drawCircle(
                        color = ClumoColors.LitDot.copy(alpha = litAlpha),
                        radius = dotRadius,
                        center = center,
                    )
                } else {
                    drawCircle(
                        color = ClumoColors.OffDot,
                        radius = dotRadius,
                        center = center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Segmented control (pill with white active thumb)
// ---------------------------------------------------------------------------

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ClumoColors.SegBackground)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (active) {
                            Modifier.shadow(3.dp, RoundedCornerShape(999.dp))
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) ClumoColors.White else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = RoundedFontFamily,
                    color = if (active) ClumoColors.Text else ClumoColors.SegInactiveText,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scanning dots animation
// ---------------------------------------------------------------------------

@Composable
fun ScanningIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val transition = rememberInfiniteTransition(label = "scanDots")
        for (i in 0 until 3) {
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "scanDot$i",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ClumoColors.Sage.copy(alpha = alpha)),
            )
        }
        Text(
            text = stringResource(R.string.list_scanning),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Sliders
// ---------------------------------------------------------------------------

@Composable
fun ClumoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..100f,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = ClumoColors.Sage,
            activeTrackColor = ClumoColors.Sage,
            inactiveTrackColor = ClumoColors.ChipBorder,
        ),
    )
}

// ---------------------------------------------------------------------------
// Text input + name dialogs (rename device / save pattern)
// ---------------------------------------------------------------------------

@Composable
fun ClumoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = RoundedFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ClumoColors.Text,
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ClumoColors.Background)
                    .border(1.5.dp, ClumoColors.OutlineBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        fontFamily = RoundedFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClumoColors.MutedLight,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun NameInputDialog(
    title: String,
    initialValue: String,
    placeholder: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(ClumoColors.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            ClumoTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinePillButton(
                    text = stringResource(R.string.dialog_cancel),
                    onClick = onDismiss,
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
                CoralPillButton(
                    text = stringResource(R.string.dialog_save),
                    onClick = { onConfirm(text) },
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** CLumo-styled two-action dialog used for blocking, user-actionable problems. */
@Composable
fun ClumoActionDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = stringResource(R.string.dialog_cancel),
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(ClumoColors.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Text(
                text = body,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                lineHeight = 21.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinePillButton(
                    text = dismissText,
                    onClick = onDismiss,
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
                CoralPillButton(
                    text = confirmText,
                    onClick = onConfirm,
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dashed rounded border (empty states / "add new" tile)
// ---------------------------------------------------------------------------

fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    cornerRadius: Dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 7.dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f
        ),
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
    )
}
