package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.contentToneFor
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.ui.components.DeviceFace
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.dashedBorder
import io.github.cespresso.clumo.ui.components.toComposeColor
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

@Composable
internal fun PatternsSection(
    patterns: List<Pattern>,
    selectedId: String?,
    appearance: DeviceAppearance,
    onSelect: (Pattern) -> Unit,
    onAddNew: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 3-column grid of saved patterns plus the "add new" tile.
        val tiles: List<Pattern?> = patterns + listOf<Pattern?>(null)
        tiles.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowItems.forEach { pattern ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (pattern == null) {
                            AddPatternTile(onClick = onAddNew)
                        } else {
                            PatternTile(
                                pattern = pattern,
                                selected = pattern.id == selectedId,
                                appearance = appearance,
                                onClick = { onSelect(pattern) },
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        OutlinePillButton(
            text = stringResource(R.string.patterns_edit_selected),
            onClick = onEdit,
            fontSize = 14.sp,
            verticalPadding = 13.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            enabled = selectedId != null,
        )
    }
}

@Composable
private fun PatternTile(
    pattern: Pattern,
    selected: Boolean,
    appearance: DeviceAppearance,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box {
            DeviceFace(
                bits = FaceBits.fromBitsString(pattern.bits),
                frameColor = appearance.enclosureColor.toComposeColor(),
                ledColor = appearance.ledColor.toComposeColor(),
                frameOutline = when {
                    selected -> ClumoColors.Text
                    contentToneFor(appearance.enclosureColor) == ContentTone.Dark -> {
                        ClumoColors.OutlineBorder
                    }
                    else -> null
                },
                size = 96.dp,
                frameCorner = 22.dp,
                framePadding = 12.dp,
                innerCorner = 11.dp,
                gridPadding = 7.dp,
                glow = false,
            )
            if (selected) {
                val accents = LocalClumoAccents.current
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(24.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(accents.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RoundedFontFamily,
                        color = accents.onAccent,
                    )
                }
            }
        }
        Text(
            text = pattern.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = if (selected) ClumoColors.Text else ClumoColors.Muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddPatternTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .dashedBorder(ClumoColors.Chevron, 2.5.dp, 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "＋",
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.MutedLight,
            )
        }
        Text(
            text = stringResource(R.string.patterns_add_new),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            maxLines = 1,
        )
    }
}
