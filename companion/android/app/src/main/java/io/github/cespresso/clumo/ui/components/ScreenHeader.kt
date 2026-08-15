package io.github.cespresso.clumo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

/**
 * The bar every screen below the list wears: a back chevron and a title, with [trailing] for
 * whatever that screen puts on the right. Shared so the four of them cannot drift apart, which
 * is a thing the eye catches immediately when moving between screens.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "‹",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
