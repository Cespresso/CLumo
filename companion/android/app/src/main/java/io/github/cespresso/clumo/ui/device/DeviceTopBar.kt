package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.components.ScreenHeader

/** The shared header, plus the three dots that open the device menu. */
@Composable
internal fun DeviceTopBar(
    title: String,
    onBack: () -> Unit,
    onToggleMenu: () -> Unit,
) {
    ScreenHeader(title = title, onBack = onBack) {
        MenuDots(onClick = onToggleMenu)
    }
}

@Composable
private fun MenuDots(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.5.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(ClumoColors.Muted),
            )
        }
    }
}
