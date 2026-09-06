package io.github.cespresso.clumo.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.design.ContentTone
import io.github.cespresso.clumo.design.contentToneFor
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.ui.components.ClumoActionDialog
import io.github.cespresso.clumo.ui.components.ClumoToggleSwitch
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.DeviceFace
import io.github.cespresso.clumo.ui.components.NameInputDialog
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.components.toComposeColor
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

@Composable
fun PatternEditorScreen(
    viewModel: PatternEditorViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = ui.existing
    val appearance = ui.appearance
    val cells = ui.cells
    val livePreview = ui.livePreview
    val updating = ui.updating
    val operationFailed = ui.operationFailed
    var saveOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == PatternEditorEvent.Close) onBack()
        }
    }

    // Always own back dispatch so there is no recomposition window in which
    // the parent handler can remove this screen during persistence.
    BackHandler {
        if (!updating) onBack()
    }

    val title = existing?.name ?: stringResource(R.string.editor_new_title)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
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
                    .clickable(enabled = !updating, onClick = onBack)
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
            val accents = LocalClumoAccents.current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (updating) ClumoColors.MutedLight else accents.cta)
                    .clickable(enabled = !updating) { saveOpen = true }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(
                        if (updating) R.string.editor_updating else R.string.editor_save
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedFontFamily,
                    color = if (updating) ClumoColors.White else accents.onCta,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Editable grid card
            Box(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(ClumoColors.White)
                    .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
                    .padding(16.dp),
            ) {
                EditorGrid(
                    cells = cells,
                    ledColor = appearance.ledColor.toComposeColor(),
                    onCellsChange = viewModel::onCellsChanged,
                )
            }

            // Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinePillButton(
                    text = stringResource(R.string.editor_clear),
                    onClick = { viewModel.onCellsChanged(FaceBits.EMPTY) },
                    fontSize = 14.sp,
                    verticalPadding = 13.dp,
                    modifier = Modifier.weight(1f),
                )
                OutlinePillButton(
                    text = stringResource(R.string.editor_invert),
                    onClick = { viewModel.onCellsChanged(cells.inv()) },
                    fontSize = 14.sp,
                    verticalPadding = 13.dp,
                    modifier = Modifier.weight(1f),
                )
            }

            // Live preview switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ClumoColors.White)
                    .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.editor_live_preview),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = RoundedFontFamily,
                    color = ClumoColors.Text,
                    modifier = Modifier.weight(1f),
                )
                ClumoToggleSwitch(
                    checked = livePreview,
                    onCheckedChange = viewModel::onLivePreviewChanged,
                )
            }

            if (livePreview) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeviceFace(
                        bits = cells,
                        frameColor = appearance.enclosureColor.toComposeColor(),
                        ledColor = appearance.ledColor.toComposeColor(),
                        frameOutline = if (
                            contentToneFor(appearance.enclosureColor) == ContentTone.Dark
                        ) {
                            ClumoColors.OutlineBorder
                        } else {
                            null
                        },
                        size = 104.dp,
                        frameCorner = 24.dp,
                        framePadding = 13.dp,
                        innerCorner = 12.dp,
                        gridPadding = 8.dp,
                        shadowElevation = 8.dp,
                    )
                    Text(
                        text = stringResource(R.string.editor_sending),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.Muted,
                    )
                }
            }

            // Destructive delete (existing patterns only)
            if (existing != null) {
                OutlinePillButton(
                    text = stringResource(R.string.editor_delete),
                    onClick = { deleteOpen = true },
                    fontSize = 14.sp,
                    verticalPadding = 13.dp,
                    textColor = ClumoColors.Coral,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !updating,
                )
            }
        }
    }

    if (saveOpen) {
        val fallbackPrefix = stringResource(R.string.pattern_default_name_prefix)
        NameInputDialog(
            title = stringResource(R.string.editor_save_dialog_title),
            initialValue = existing?.name ?: "",
            placeholder = stringResource(R.string.editor_name_placeholder),
            onConfirm = { rawName ->
                saveOpen = false
                viewModel.save(rawName, fallbackPrefix)
            },
            onDismiss = { saveOpen = false },
        )
    }

    if (deleteOpen && existing != null) {
        DeleteConfirmDialog(
            onConfirm = {
                deleteOpen = false
                viewModel.delete()
            },
            onDismiss = { deleteOpen = false },
        )
    }

    if (operationFailed) {
        ClumoActionDialog(
            title = stringResource(R.string.editor_operation_error_title),
            body = stringResource(R.string.editor_operation_error_body),
            confirmText = stringResource(R.string.action_retry),
            onConfirm = viewModel::retry,
            onDismiss = viewModel::dismissFailure,
        )
    }
}

/** 8x8 paint grid with tap-and-drag painting toward the drag's initial value. */
@Composable
private fun EditorGrid(
    cells: Long,
    ledColor: Color,
    onCellsChange: (Long) -> Unit,
) {
    val gap = 8.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    BoxWithConstraints {
        val cell = minOf(34.dp, (maxWidth - gap * 7) / 8)
        val gridSize = cell * 8 + gap * 7
        // Latest state for the gesture handler without restarting pointerInput.
        val currentCells = rememberUpdatedState(cells)

        Canvas(
            modifier = Modifier
                .size(gridSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val first = cellIndexAt(down.position, size, gapPx)
                        val paintOn = first?.let {
                            (currentCells.value shr it) and 1L == 0L
                        } ?: true
                        first?.let {
                            onCellsChange(setBit(currentCells.value, it, paintOn))
                        }
                        down.consume()
                        drag(down.id) { change ->
                            cellIndexAt(change.position, size, gapPx)?.let {
                                onCellsChange(setBit(currentCells.value, it, paintOn))
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            val cellPx = (size.width - gapPx * 7) / 8f
            val step = cellPx + gapPx
            val radius = cellPx / 2f
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val on = (cells shr (row * 8 + col)) and 1L == 1L
                    val center = Offset(col * step + radius, row * step + radius)
                    if (on) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ledColor.copy(alpha = 0.5f),
                                    ledColor.copy(alpha = 0f),
                                ),
                                center = center,
                                radius = radius * 1.7f,
                            ),
                            radius = radius * 1.7f,
                            center = center,
                        )
                        drawCircle(ledColor, radius, center)
                    } else {
                        drawCircle(ClumoColors.EditorOffCell, radius, center)
                    }
                }
            }
        }
    }
}

private fun cellIndexAt(position: Offset, size: IntSize, gapPx: Float): Int? {
    if (position.x < 0 || position.y < 0) return null
    if (position.x > size.width || position.y > size.height) return null
    val cellPx = (size.width - gapPx * 7) / 8f
    val step = cellPx + gapPx
    val col = (position.x / step).toInt().coerceIn(0, 7)
    val row = (position.y / step).toInt().coerceIn(0, 7)
    return row * 8 + col
}

private fun setBit(mask: Long, index: Int, on: Boolean): Long =
    if (on) mask or (1L shl index) else mask and (1L shl index).inv()

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(ClumoColors.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_delete_confirm_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Text(
                text = stringResource(R.string.editor_delete_confirm_body),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                textAlign = TextAlign.Start,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinePillButton(
                    text = stringResource(R.string.dialog_cancel),
                    onClick = onDismiss,
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
                CtaPillButton(
                    text = stringResource(R.string.editor_delete_confirm),
                    onClick = onConfirm,
                    fontSize = 14.sp,
                    verticalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
