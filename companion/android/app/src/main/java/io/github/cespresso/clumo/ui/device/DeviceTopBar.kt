package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.ui.components.ScreenHeader

/** The shared header, plus the three dots that open the device menu. */
@Composable
internal fun DeviceTopBar(
    title: String,
    onBack: () -> Unit,
    onToggleMenu: () -> Unit,
    connection: ConnectionState? = null,
    failureMessage: String = "",
    recoveryLabel: String = "Reconnect",
    onRecover: () -> Unit = {},
) {
    Column {
        ScreenHeader(
            title = title,
            onBack = onBack,
            titleAccessory = connection?.let { link ->
                { ConnectionBadge(link, failureMessage) }
            },
        ) { MenuDots(onClick = onToggleMenu) }
        if (connection != null && connection != ConnectionState.Ready) {
            Row(Modifier.padding(start = 54.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(connection.label(), style = MaterialTheme.typography.bodySmall, color = ClumoColors.Muted)
                if (connection == ConnectionState.Disconnected || connection == ConnectionState.Error) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRecover) { Text(recoveryLabel) }
                }
            }
        }
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

private fun ConnectionState.label(): String =
    when (this) {
        ConnectionState.Ready -> "Connected"
        ConnectionState.Connected, ConnectionState.Synchronizing -> "Syncing…"
        ConnectionState.Connecting -> "Connecting…"
        ConnectionState.Reconnecting -> "Reconnecting…"
        ConnectionState.Bonding -> "Pairing…"
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Error -> "Connection failed"
    }

@Composable
private fun ConnectionBadge(connection: ConnectionState, failureMessage: String) {
    var expanded by remember { mutableStateOf(false) }
    val ready = connection == ConnectionState.Ready
    val inactive = connection == ConnectionState.Disconnected || connection == ConnectionState.Error
    val ink = if (ready) {
        Color(0xFF718A64)
    } else if (inactive) {
        Color(0xFFAC5B44)
    } else {
        ClumoColors.Muted
    }
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Connection status: ${connection.label()}"
            },
        ) {
            if (!ready && !inactive) {
                CircularProgressIndicator(Modifier.size(18.dp), color = ink, strokeWidth = 2.dp)
            } else {
                Canvas(Modifier.size(20.dp)) {
                    val w = size.width
                    drawCircle(ink, radius = w * .43f, style = Stroke(w * .075f))
                    val mark = Path().apply {
                        if (ready) {
                            moveTo(w * .25f, w * .5f)
                            lineTo(w * .43f, w * .68f)
                            lineTo(w * .75f, w * .32f)
                        } else {
                            moveTo(w * .28f, w * .5f)
                            lineTo(w * .72f, w * .5f)
                        }
                    }
                    drawPath(mark, ink, style = Stroke(w * .085f, cap = StrokeCap.Round))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(connection.label(), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        ready -> "Ready to configure this device."
                        inactive && failureMessage.isNotBlank() -> failureMessage
                        inactive -> "Connect to change device settings."
                        connection == ConnectionState.Bonding -> "Complete Bluetooth pairing to continue."
                        connection == ConnectionState.Connected || connection == ConnectionState.Synchronizing -> "Reading device settings."
                        else -> "Waiting for the device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}
