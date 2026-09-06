package io.github.cespresso.clumo.ui.device

import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.cespresso.clumo.R

// ---------------------------------------------------------------------------
// Wording
//
// DeviceUiState names what to say; these resolve it. Keeping the strings here
// is what lets the rules above be plain unit tests.
// ---------------------------------------------------------------------------

@Composable
internal fun DeviceStateLabel.text(reconnectAttempt: Int): String =
    when (this) {
        DeviceStateLabel.Connecting -> stringResource(R.string.state_connecting)
        DeviceStateLabel.Reconnecting -> stringResource(R.string.state_reconnecting, reconnectAttempt)
        DeviceStateLabel.Pairing -> stringResource(R.string.state_pairing)
        DeviceStateLabel.Discovering -> stringResource(R.string.state_discovering)
        DeviceStateLabel.Synchronizing -> stringResource(R.string.state_synchronizing)
        DeviceStateLabel.Connected -> stringResource(R.string.state_connected)
        DeviceStateLabel.Error -> stringResource(R.string.state_error)
        DeviceStateLabel.Disconnected -> stringResource(R.string.state_disconnected)
    }

@Composable
internal fun DeviceFailureMessage.text(): String =
    when (this) {
        DeviceFailureMessage.Unavailable -> stringResource(R.string.connection_error_unavailable)
        DeviceFailureMessage.BluetoothOff -> stringResource(R.string.connection_error_bluetooth_off)
        DeviceFailureMessage.Permission -> stringResource(R.string.connection_error_permission)
        DeviceFailureMessage.Timeout -> stringResource(R.string.connection_error_timeout)
        DeviceFailureMessage.Pairing -> stringResource(R.string.connection_error_pairing)
        DeviceFailureMessage.Services -> stringResource(R.string.connection_error_services)
        DeviceFailureMessage.Incompatible -> stringResource(R.string.connection_error_incompatible)
        DeviceFailureMessage.Sync -> stringResource(R.string.connection_error_sync)
        DeviceFailureMessage.Lost -> stringResource(R.string.device_error_banner)
    }

@Composable
internal fun DeviceFailureAction.label(): String =
    when (this) {
        // Both settings destinations read the same on the button; only where they land differs.
        DeviceFailureAction.OpenAppSettings,
        DeviceFailureAction.OpenBluetoothSettings,
        -> stringResource(R.string.action_open_settings)

        DeviceFailureAction.BackToList -> stringResource(R.string.action_back_to_list)
        DeviceFailureAction.Retry -> stringResource(R.string.device_error_retry)
    }
