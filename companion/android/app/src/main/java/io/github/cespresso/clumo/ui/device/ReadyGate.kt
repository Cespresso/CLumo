package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled

/**
 * Wraps controls that only mean anything over a live link. While the device is not ready they
 * are dimmed, hidden from screen readers, and covered by a pane that eats the touch, so a tap
 * that could not have reached the device never looks like it did.
 */
@Composable
internal fun ReadyGate(
    ready: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .alpha(if (ready) 1f else 0.45f)
                .then(
                    if (ready) Modifier else Modifier.clearAndSetSemantics { disabled() }
                )
                .then(modifier),
        ) {
            content()
        }
        if (!ready) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clearAndSetSemantics {},
            )
        }
    }
}
