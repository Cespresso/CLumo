package io.github.cespresso.clumo.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

/**
 * String resources describing what each physical button does in a mode.
 * `main` is the coral knob, `sub` the white one.
 */
data class ButtonRoleLabels(
    @StringRes val main: Int,
    @StringRes val sub: Int,
)

/** Unknown modes fall back to Pomodoro, matching the firmware's own default. */
fun buttonRoleLabels(mode: Int): ButtonRoleLabels = when (mode) {
    BleUuids.MODE_TIMER -> ButtonRoleLabels(
        R.string.button_role_timer_main,
        R.string.button_role_timer_sub,
    )
    BleUuids.MODE_DISPLAY -> ButtonRoleLabels(
        R.string.button_role_display_main,
        R.string.button_role_display_sub,
    )
    BleUuids.MODE_VISUALIZER -> ButtonRoleLabels(
        R.string.button_role_viz_main,
        R.string.button_role_viz_sub,
    )
    else -> ButtonRoleLabels(
        R.string.button_role_pomodoro_main,
        R.string.button_role_pomodoro_sub,
    )
}

/** Two chips telling what the physical main/sub buttons do in [mode]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRoleTags(mode: Int, modifier: Modifier = Modifier) {
    val labels = buttonRoleLabels(mode)
    // Two chips stop fitting side by side as the font scale grows, so this wraps
    // the second one rather than let its label break mid-word. Keep the alignment
    // argument on spacedBy: it centers a wrapped row, and filling the width leaves
    // the parent's own centering with nothing to do.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoleTag(
            textRes = labels.main,
            background = ClumoColors.CoralChipBg,
            textColor = ClumoColors.CoralChipFg,
            dotColor = ClumoColors.Coral,
        )
        RoleTag(
            textRes = labels.sub,
            background = ClumoColors.White,
            textColor = ClumoColors.Muted,
            dotColor = ClumoColors.White,
            dotBorder = ClumoColors.Gray,
            border = ClumoColors.OutlineBorder,
        )
    }
}

@Composable
private fun RoleTag(
    @StringRes textRes: Int,
    background: Color,
    textColor: Color,
    dotColor: Color,
    dotBorder: Color? = null,
    border: Color? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
                .then(
                    if (dotBorder != null) Modifier.border(1.5.dp, dotBorder, CircleShape)
                    else Modifier
                ),
        )
        Text(
            text = stringResource(textRes),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = textColor,
            maxLines = 1,
        )
    }
}
