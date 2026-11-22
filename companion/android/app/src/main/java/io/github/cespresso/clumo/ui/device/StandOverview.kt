package io.github.cespresso.clumo.ui.device

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.components.product.StandPart

@Composable
internal fun StandOverview(
    ui: DeviceUiState,
    assignments: Map<StandPart, String>,
    select: (StandPart) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val patternName = remember(context, ui.standDisplay, ui.standbyMode, ui.ready) {
        val live = ui.standDisplay?.takeIf { ui.ready }?.let(StandbyPattern::fromDevice)
        if (live == null) {
            null
        } else {
            val library = readStandbyLibrary(context.getSharedPreferences("standby_patterns_v1", Context.MODE_PRIVATE))
            (StandbyPattern.presets + library.values.sortedBy { it.name } + StandbyPattern.off)
                .firstOrNull { it.matches(live) }?.name
        }
    }
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled, role = Role.Button) { select(StandPart.Matrix) }
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (!ui.ready) {
                    "LED display"
                } else if (ui.visualizerActive) {
                    "Audio visualizer"
                } else {
                    "Standby display"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = ClumoColors.Text,
            )
            if (!ui.visualizerActive && patternName != null) {
                Text(
                    patternName,
                    Modifier.widthIn(max = 160.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = ClumoColors.Text,
                    maxLines = 2,
                )
            }
            Text("›", color = ClumoColors.Text)
        }
        HorizontalDivider(color = ClumoColors.Text.copy(alpha = .18f))
        Row(Modifier.fillMaxWidth()) {
            for ((part, label) in listOf(
                StandPart.LeftButton to "Left button",
                StandPart.RightButton to "Right button",
                StandPart.Knob to "Knob · Rotate",
            )) {
                Column(
                    Modifier.weight(1f).clickable(enabled, role = Role.Button) { select(part) }
                        .heightIn(min = 56.dp).padding(end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = ClumoColors.Text)
                    Text(
                        assignments[part] ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ClumoColors.Text,
                    )
                }
            }
        }
    }
}
