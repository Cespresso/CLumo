package io.github.cespresso.clumo.ui.devices

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.ScanFailure
import io.github.cespresso.clumo.ui.components.ScreenHeader
import io.github.cespresso.clumo.ui.components.bluetoothPermissions
import io.github.cespresso.clumo.ui.components.product.GalleryDevice
import io.github.cespresso.clumo.ui.components.product.ProductDisplayState
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents

/** First-seen order stays stable as RSSI changes. GATT compatibility is not authentication. */
internal fun discoveryGallery(state: DeviceListUiState): List<GalleryDevice> =
    state.discoveredDevices.map { advertisement ->
        val known = state.knownDevices.firstOrNull { it.device.address == advertisement.address }
        val link = state.connectionStates[advertisement.address] ?: ConnectionState.Disconnected
        val identified = known != null && link == ConnectionState.Ready
        GalleryDevice(
            id = "find:${advertisement.address}",
            address = advertisement.address,
            name = "${known?.name ?: advertisement.name ?: "CLumo"} · ${advertisement.address.takeLast(5)}",
            kind = if (identified) requireNotNull(known).device.kind else DeviceKind.CLUMO,
            unidentified = !identified,
            display = if (identified) requireNotNull(known).let { ProductDisplayState(it.appearance, link, it.mirrorBits, it.brightnessLevel, it.volumeSegments) } else null,
            status = when (link) {
                ConnectionState.Ready -> "Connected"
                ConnectionState.Connecting, ConnectionState.Reconnecting -> "Connecting…"
                ConnectionState.Connected, ConnectionState.Synchronizing -> "Checking device…"
                ConnectionState.Bonding -> "Confirm pairing on your phone"
                ConnectionState.Error -> "Connection failed"
                ConnectionState.Disconnected -> "Available"
            },
        )
    }

/** Root-owned input bridge; this destination owns overlays/permissions, never a SceneView. */
internal class FindSceneActions {
    var scan: () -> Unit = {}
    var openDevice: (String) -> Unit = {}
}

@Composable
internal fun FindDevices3DScreen(
    state: DeviceListUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String, String?) -> Unit,
    onBack: () -> Unit,
    actions: FindSceneActions,
    selected: GalleryDevice?,
    onGuide: () -> Unit,
    autoStartScan: Boolean,
    onScanStart: () -> Unit,
    onResetSelection: () -> Unit,
    onOpenDevice: (String) -> Unit,
) {
    val context = LocalContext.current
    var requestedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = !bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!permissionDenied) pendingAction?.invoke()
        pendingAction = null
    }
    fun withPermission(action: () -> Unit) {
        val missing = bluetoothPermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            permissionDenied = false
            action()
        } else {
            pendingAction = action
            launcher.launch(missing.toTypedArray())
        }
    }
    fun scan() =
        withPermission {
            requestedAddress = null
            onResetSelection()
            onScan()
        }
    val markScanStarted by androidx.compose.runtime.rememberUpdatedState(onScanStart)
    LaunchedEffect(autoStartScan) {
        if (autoStartScan) {
            markScanStarted()
            scan()
        }
    }
    val devices = discoveryGallery(state)
    val connecting = requestedAddress?.let { state.connectionStates[it] }
    val busy = connecting != null && connecting !in listOf(ConnectionState.Error, ConnectionState.Disconnected, ConnectionState.Ready)
    val permissionIssue = permissionDenied || state.scanFailure == ScanFailure.PermissionDenied
    val message = when {
        permissionIssue -> "Allow nearby devices to search for CLumo."
        state.scanFailure == ScanFailure.BluetoothDisabled -> "Turn on Bluetooth to find devices."
        state.scanFailure == ScanFailure.BluetoothUnavailable -> "Bluetooth is unavailable on this phone."
        state.scanFailure == ScanFailure.ScanFailed -> "Search failed. Try scanning again."
        busy -> devices.firstOrNull { it.address == requestedAddress }?.status ?: "Connecting…"
        connecting == ConnectionState.Error -> "Could not connect. Try again."
        selected?.unidentified == false -> "Connected"
        selected != null -> "Select a device to connect"
        state.scanning -> "Looking for nearby devices…"
        else -> "No devices found. Check that your device is ready to pair."
    }
    SideEffect {
        actions.scan = { if (!busy) scan() }
        actions.openDevice = { address ->
            if (state.connectionStates[address] == ConnectionState.Ready && state.knownDevices.any { it.device.address == address }) {
                onOpenDevice(address)
            } else if (!busy) {
                withPermission {
                    requestedAddress = address
                    onConnect(address, state.discoveredDevices.firstOrNull { it.address == address }?.name)
                }
            }
        }
    }
    val accents = LocalClumoAccents.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("find-3d")) {
        ScreenHeader("Find devices", onBack) {
            TextButton(onClick = { if (state.scanning) onStopScan() else scan() }, enabled = !busy) {
                Text(if (state.scanning) "Stop scan" else "Scan again")
            }
        }
        val wide = maxWidth > maxHeight
        Column(
            Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                .fillMaxWidth(if (wide) .48f else 1f).safeDrawingPadding()
                .heightIn(max = maxHeight * .72f).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected != null) {
                if (devices.size > 1) Text("${devices.indexOfFirst { it.id == selected.id } + 1} / ${devices.size}")
                Text(selected.name, style = MaterialTheme.typography.headlineSmall)
                val rssi = state.discoveredDevices.firstOrNull { it.address == selected.address }?.rssi
                if (selected.unidentified && !busy && rssi != null && rssi in -126..-1) {
                    Text(
                        when {
                            rssi >= -65 -> "Strong signal"
                            rssi >= -80 -> "Moderate signal"
                            else -> "Weak signal"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(message, modifier = Modifier.testTag("find-status"))
            if (permissionIssue || state.scanFailure == ScanFailure.BluetoothDisabled) {
                TextButton(onClick = {
                    val intent = if (permissionIssue) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    } else {
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    }
                    context.startActivity(intent)
                }) { Text(if (permissionIssue) "Permission settings" else "Bluetooth settings") }
            } else if (selected?.address != null) {
                Button(
                    onClick = { actions.openDevice(selected.address) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.cta, contentColor = accents.onCta),
                ) {
                    Text(
                        if (!selected.unidentified) {
                            "Open device"
                        } else if (busy) {
                            "Connecting…"
                        } else {
                            "Connect"
                        },
                    )
                }
            }
            TextButton(onClick = onGuide, enabled = !busy) { Text("Show me how to pair") }
        }
    }
}
