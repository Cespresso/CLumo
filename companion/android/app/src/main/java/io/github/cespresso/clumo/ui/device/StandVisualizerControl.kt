package io.github.cespresso.clumo.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Explicit user control: displaying or returning to this screen never starts audio capture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StandVisualizerControl(
    active: Boolean,
    ready: Boolean,
    accent: Color,
    onStart: () -> Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
) {
    val context = LocalContext.current
    val currentReady by rememberUpdatedState(ready)
    val currentStart by rememberUpdatedState(onStart)
    var message by remember { mutableStateOf<String?>(null) }
    var requestingPermission by remember { mutableStateOf(false) }
    fun start() {
        message = when {
            !currentReady -> "Connect your device to start."
            !currentStart() -> "Could not start audio visualizer. Please try again."
            else -> null
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestingPermission = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            message = "Audio permission is required to start the visualizer."
        }
    }
    LaunchedEffect(active) { if (active) message = null }
    val selectedContainer = accent
    val selectedContent = Color(0xFFFFF9F2)
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = selectedContainer,
        activeContentColor = selectedContent,
    )
    Column(modifier.testTag("stand-visualizer")) {
        if (showHeading) {
            Text("LED display", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !active,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors,
                icon = { SegmentedButtonDefaults.Icon(active = !active) { DisplayModeIcon(visualizer = false) } },
                enabled = (active || ready) && !requestingPermission,
                modifier = Modifier.weight(1f).testTag("stand-display-standby"),
                onClick = {
                    message = null
                    if (active) onStop()
                },
            ) { Text("Standby") }
            SegmentedButton(
                selected = active,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors,
                icon = { SegmentedButtonDefaults.Icon(active = active) { DisplayModeIcon(visualizer = true) } },
                enabled = ready && !requestingPermission,
                modifier = Modifier.weight(1f).testTag("stand-display-visualizer"),
                onClick = {
                    message = null
                    if (!active) {
                        val permissions = buildList {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (permissions.isEmpty()) {
                            start()
                        } else {
                            requestingPermission = true
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    }
                },
            ) { Text("Visualizer") }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message ?: when {
                requestingPermission -> "Waiting for permission…"
                !ready -> "Connect your device to change the display."
                else -> "Choose the LED display source."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Only the source pictogram is custom; selection/check animation belongs to Material. */
@Composable
private fun DisplayModeIcon(visualizer: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        if (visualizer) {
            listOf(0.42f, 0.92f, 0.65f).forEachIndexed { index, height ->
                drawRoundRect(
                    color,
                    Offset(size.width * (0.09f + index * 0.3f), size.height * (1f - height)),
                    Size(size.width * 0.18f, size.height * height),
                    CornerRadius(size.width * 0.09f),
                )
            }
        } else {
            repeat(3) { row ->
                repeat(3) { column ->
                    drawCircle(
                        color,
                        size.width * 0.105f,
                        Offset(size.width * (0.16f + column * 0.34f), size.height * (0.16f + row * 0.34f)),
                    )
                }
            }
        }
    }
}
