package io.github.cespresso.clumo.ui.devices

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.ScanFailure
import io.github.cespresso.clumo.ui.components.BrandCorner
import io.github.cespresso.clumo.ui.components.bluetoothPermissions
import io.github.cespresso.clumo.ui.components.ClumoActionDialog
import io.github.cespresso.clumo.ui.components.ClumoDevice
import io.github.cespresso.clumo.ui.components.ClumoInfoDialog
import io.github.cespresso.clumo.ui.components.ClumoNavigationBarSpacer
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.HelpBadge
import io.github.cespresso.clumo.ui.components.ScanningIndicator
import io.github.cespresso.clumo.ui.components.connectionLabel
import io.github.cespresso.clumo.ui.components.dashedBorder
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

private val LIST_ITEM_GAP = 16.dp
private val LIST_OVERLAY_CLEARANCE = 120.dp

private sealed interface PendingBluetoothAction {
    data object Scan : PendingBluetoothAction
    data class Connect(val address: String, val name: String?) : PendingBluetoothAction
}

@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel,
    onOpenDevice: (address: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val knownDevices = ui.knownDevices
    val foundDevices = ui.foundDevices
    val scanning = ui.scanning
    val scanFailure = ui.scanFailure
    val scanFinishedEmpty = ui.scanFinishedEmpty
    var pendingAction by remember { mutableStateOf<PendingBluetoothAction?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPrimaryHelp by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            when (val action = pendingAction) {
                PendingBluetoothAction.Scan -> {
                    viewModel.startScan()
                }
                is PendingBluetoothAction.Connect -> {
                    viewModel.connect(action.address, action.name)
                    onOpenDevice(action.address)
                }
                null -> Unit
            }
        } else {
            showPermissionDialog = true
        }
        pendingAction = null
    }

    fun runWithBluetoothPermission(action: PendingBluetoothAction) {
        val missing = bluetoothPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            when (action) {
                PendingBluetoothAction.Scan -> {
                    viewModel.startScan()
                }
                is PendingBluetoothAction.Connect -> {
                    viewModel.connect(action.address, action.name)
                    onOpenDevice(action.address)
                }
            }
        }
    }

    fun startScan() = runWithBluetoothPermission(PendingBluetoothAction.Scan)

    Box(modifier = Modifier.fillMaxSize()) {
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
                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrandCorner(size = 30.dp, stroke = 9.dp)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.list_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.Text,
                    )
                    HelpBadge(
                        description = stringResource(R.string.primary_help_open),
                        onClick = { showPrimaryHelp = true },
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(ClumoColors.White)
                        .border(1.5.dp, ClumoColors.ChipBorder, RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.list_settings),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedFontFamily,
                        color = ClumoColors.Muted,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 22.dp,
                    end = 22.dp,
                    top = 8.dp,
                    // The trailing spacer is a lazy item too, so the arrangement gap before it
                    // already counts toward the clearance below the last card.
                    bottom = LIST_OVERLAY_CLEARANCE - LIST_ITEM_GAP,
                ),
                verticalArrangement = Arrangement.spacedBy(LIST_ITEM_GAP),
            ) {
                items(knownDevices, key = { it.device.id }) { item ->
                    KnownDeviceCard(
                        item = item,
                        onTogglePrimary = { viewModel.togglePrimary(item.device.id) },
                        onTap = {
                            viewModel.stopScan()
                            runWithBluetoothPermission(
                                PendingBluetoothAction.Connect(
                                    item.device.address,
                                    item.device.name,
                                )
                            )
                        },
                    )
                }

                if (knownDevices.isEmpty() && foundDevices.isEmpty() &&
                    scanFailure == null && !scanFinishedEmpty
                ) {
                    item { EmptyStateCard() }
                }

                if (scanFailure != null || scanFinishedEmpty) {
                    item {
                        ScanStatusCard(
                            failure = scanFailure,
                            empty = scanFinishedEmpty,
                            onAction = {
                                if (scanFailure == ScanFailure.BluetoothDisabled) {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } else {
                                    startScan()
                                }
                            },
                        )
                    }
                }

                if (scanning) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ScanningIndicator()
                        }
                    }
                }

                if (foundDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.list_found),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.Muted,
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    items(foundDevices, key = { it.address }) { adv ->
                        FoundDeviceRow(
                            advertisement = adv,
                            onConnect = {
                                viewModel.stopScan()
                                runWithBluetoothPermission(
                                    PendingBluetoothAction.Connect(adv.address, adv.name)
                                )
                            },
                        )
                    }
                    item { PasskeyHint() }
                }

                item { ClumoNavigationBarSpacer(visualSpacing = 0.dp) }
            }
        }

        // Pinned scan button over a soft gradient.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to ClumoColors.Background.copy(alpha = 0f),
                        0.45f to ClumoColors.Background,
                    )
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 26.dp),
        ) {
            CtaPillButton(
                text = stringResource(R.string.list_scan_button),
                onClick = { if (!scanning) startScan() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showPermissionDialog) {
        ClumoActionDialog(
            title = stringResource(R.string.permission_dialog_title),
            body = stringResource(R.string.permission_dialog_body),
            confirmText = stringResource(R.string.action_open_settings),
            onConfirm = {
                showPermissionDialog = false
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    if (showPrimaryHelp) {
        ClumoInfoDialog(
            title = stringResource(R.string.device_primary_marker),
            body = stringResource(R.string.primary_help_body),
            onDismiss = { showPrimaryHelp = false },
        )
    }
}

@Composable
private fun KnownDeviceCard(
    item: KnownDeviceCardState,
    onTogglePrimary: () -> Unit,
    onTap: () -> Unit,
) {
    val state = item.connectionState
    val (label, labelColor) = connectionLabel(state)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp), spotColor = Color(0x14000000))
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ClumoDevice(
            bits = if (state == ConnectionState.Ready) item.mirrorBits else FaceBits.EMPTY,
            size = 94.dp,
            appearance = item.appearance,
            connectionState = state,
            litAlpha = item.litAlpha,
            glow = false,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Text,
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = labelColor,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (item.isPrimary) {
                PrimaryBadge(modifier = Modifier.padding(top = 6.dp))
            }
        }
        val toggleLabel = stringResource(
            if (item.isPrimary) R.string.device_menu_unset_primary else R.string.device_menu_set_primary
        )
        val primaryStateLabel = stringResource(R.string.device_primary_marker)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onTogglePrimary)
                .semantics { if (item.isPrimary) stateDescription = primaryStateLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (item.isPrimary) "★" else "☆",
                fontSize = 18.sp,
                fontFamily = RoundedFontFamily,
                color = if (item.isPrimary) {
                    LocalClumoAccents.current.accent
                } else {
                    ClumoColors.Chevron
                },
            )
        }
        Text(
            text = "›",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Chevron,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/**
 * Visible counterpart of the filled star, so the star's meaning is readable
 * without tapping it. Semantics are cleared because the star button already
 * announces the same state.
 */
@Composable
private fun PrimaryBadge(modifier: Modifier = Modifier) {
    val accents = LocalClumoAccents.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accents.accent)
            .clearAndSetSemantics {}
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.device_primary_badge),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = accents.onAccent,
        )
    }
}

@Composable
private fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White.copy(alpha = 0.6f))
            .dashedBorder(ClumoColors.DashedBorder, 2.dp, 28.dp)
            .padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Grayed-out device face silhouette.
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ClumoColors.Panel)
                .border(11.dp, ClumoColors.EmptyFaceBorder, RoundedCornerShape(20.dp)),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.list_empty_title))
                }
                append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(stringResource(R.string.list_empty_body))
                }
            },
            fontSize = 14.sp,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            textAlign = TextAlign.Center,
            lineHeight = 25.sp,
        )
    }
}

@Composable
private fun ScanStatusCard(
    failure: ScanFailure?,
    empty: Boolean,
    onAction: () -> Unit,
) {
    val message = when (failure) {
        ScanFailure.BluetoothDisabled -> stringResource(R.string.scan_error_bluetooth_off)
        ScanFailure.PermissionDenied -> stringResource(R.string.scan_error_permission)
        ScanFailure.BluetoothUnavailable -> stringResource(R.string.scan_error_unavailable)
        ScanFailure.ScanFailed -> stringResource(R.string.scan_error_generic)
        null -> if (empty) stringResource(R.string.scan_empty) else ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ClumoColors.ErrorBg)
            .border(1.5.dp, ClumoColors.ErrorBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.ErrorText,
        )
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accents.cta)
                .clickable(onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (failure == ScanFailure.BluetoothDisabled) {
                    stringResource(R.string.action_open_settings)
                } else {
                    stringResource(R.string.action_retry)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = accents.onCta,
            )
        }
    }
}

@Composable
private fun FoundDeviceRow(
    advertisement: DeviceAdvertisement,
    onConnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Small blank face tile.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClumoColors.Gray)
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(ClumoColors.Panel),
            )
        }
        Text(
            text = advertisement.name ?: "CLumo",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.weight(1f),
        )
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accents.cta)
                .clickable(onClick = onConnect)
                .padding(horizontal = 20.dp, vertical = 9.dp),
        ) {
            Text(
                text = stringResource(R.string.list_connect),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = accents.onCta,
            )
        }
    }
}

@Composable
private fun PasskeyHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(accents.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = accents.onAccent,
            )
        }
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.list_passkey_prefix))
                withStyle(
                    SpanStyle(fontWeight = FontWeight.ExtraBold, color = ClumoColors.Text)
                ) {
                    append(stringResource(R.string.list_passkey))
                }
                append(stringResource(R.string.list_passkey_suffix))
            },
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            lineHeight = 20.sp,
        )
    }
}
