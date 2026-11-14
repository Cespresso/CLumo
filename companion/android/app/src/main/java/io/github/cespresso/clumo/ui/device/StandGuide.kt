package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.ui.components.HelpBadge

/** Tap-accessible contextual help; dismisses on outside tap or Back. */
@Composable
internal fun StandHelp(title: String, help: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Box {
            HelpBadge(description = title, onClick = { open = !open })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Text(help, modifier = Modifier.widthIn(max = 280.dp).padding(16.dp))
            }
        }
    }
}

@Composable
internal fun StandSetupGuide() {
    StandHelp(
        title = stringResource(R.string.stand_wifi_title),
        help = stringResource(R.string.stand_wifi_steps) + "\n\n" + stringResource(R.string.stand_wifi_return) + "\n\n" + stringResource(R.string.stand_pairing_hint),
        modifier = Modifier.padding(horizontal = 22.dp),
    )
}
