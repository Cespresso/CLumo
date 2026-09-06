package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

/**
 * The sheet behind the three dots. It closes itself before running an entry, so the callbacks
 * are the action alone. [identified] gates the entries that store something against a device
 * id, which is unavailable until the link reports one.
 */
@Composable
internal fun BoxScope.DeviceMenu(
    identified: Boolean,
    isPrimary: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onAppearance: () -> Unit,
    onTogglePrimary: () -> Unit,
    onSettings: () -> Unit,
    onRefreshGatt: () -> Unit,
    onDisconnect: () -> Unit,
) {
    // A full-bleed catcher so a tap anywhere else dismisses the sheet.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.End,
                ),
            )
            .offset(x = (-16).dp, y = 52.dp)
            .widthIn(min = 176.dp)
            .width(IntrinsicSize.Max)
            .shadow(14.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(8.dp),
    ) {
        @Composable
        fun entry(label: String, color: Color, enabled: Boolean = true, action: () -> Unit) {
            MenuItem(label = label, color = color, enabled = enabled) {
                onDismiss()
                action()
            }
        }

        entry(stringResource(R.string.device_menu_rename), ClumoColors.Text, action = onRename)
        entry(
            label = stringResource(R.string.device_menu_appearance),
            color = ClumoColors.Text,
            enabled = identified,
            action = onAppearance,
        )
        entry(
            label = stringResource(
                if (isPrimary) R.string.device_menu_unset_primary else R.string.device_menu_set_primary
            ),
            color = ClumoColors.Text,
            enabled = identified,
            action = onTogglePrimary,
        )
        entry(stringResource(R.string.device_menu_settings), ClumoColors.Text, action = onSettings)
        entry(
            stringResource(R.string.device_menu_refresh_gatt),
            ClumoColors.Text,
            action = onRefreshGatt,
        )
        entry(stringResource(R.string.device_menu_disconnect), ClumoColors.Coral, action = onDisconnect)
    }
}

@Composable
private fun MenuItem(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = if (enabled) color else ClumoColors.MutedLight,
        )
    }
}
