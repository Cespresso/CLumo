package io.github.cespresso.clumo.ui.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.ui.components.bluetoothPermissions

internal enum class HomeBluetoothIssue(val message: String, val action: String?) {
    Permission("近くのデバイスへのアクセスが必要です", "アクセス許可を設定"),
    Off("Bluetoothがオフです", "Bluetoothを設定"),
    Unavailable("この端末ではBluetoothを利用できません", null),
}

@SuppressLint("MissingPermission")
private fun homeBluetoothIssue(context: Context): HomeBluetoothIssue? {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return HomeBluetoothIssue.Unavailable
    if (bluetoothPermissions().any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) return HomeBluetoothIssue.Permission
    return try {
        if (adapter.isEnabled) null else HomeBluetoothIssue.Off
    } catch (_: SecurityException) {
        HomeBluetoothIssue.Permission
    }
}

/** Brand and carousel position above the shared scene, with actionable adapter state. */
@Composable
internal fun Home3DOverlay(showHint: Boolean, onDismissHint: () -> Unit, deviceCount: Int = 0, selectedIndex: Int = -1, accent: Color = Color(0xFFB87858), onOpenSettings: () -> Unit = {}, onFindDevices: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var issue by remember { mutableStateOf(homeBluetoothIssue(context)) }
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Read the adapter itself, never trust values in an exported broadcast.
                issue = homeBluetoothIssue(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) issue = homeBluetoothIssue(context)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    Home3DChrome(showHint, onDismissHint, issue, deviceCount, selectedIndex, accent, onOpenSettings, onFindDevices) {
        val intent = when (issue) {
            HomeBluetoothIssue.Permission -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            HomeBluetoothIssue.Off -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> null
        }
        intent?.let(context::startActivity)
    }
}

@Composable
internal fun Home3DChrome(showHint: Boolean, onDismissHint: () -> Unit, issue: HomeBluetoothIssue?, deviceCount: Int = 0, selectedIndex: Int = -1, accent: Color = Color(0xFFB87858), onOpenSettings: () -> Unit = {}, onFindDevices: () -> Unit = {}, onResolveIssue: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("gallery-root")) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 26.dp, end = 12.dp, top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("clumo", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFF343831))
            // 48dp touch target + 24dp icon + 2dp vector inset gives 14dp of inner margin.
            // Together with the 12dp end padding, the visible gear aligns with the 26dp title inset.
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).testTag("home-settings")) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", modifier = Modifier.size(24.dp), tint = Color(0xFF343831))
            }
        }
        FloatingActionButton(
            onClick = onFindDevices,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp).testTag("home-add-device"),
            containerColor = Color(0xFF343831),
            contentColor = Color(0xFFF3EBDD),
        ) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Find devices") }
        if (deviceCount > 1 && !showHint) {
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 38.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // A bounded window avoids overflowing on large device collections.
                val start = (selectedIndex - 3).coerceIn(0, (deviceCount - 7).coerceAtLeast(0))
                for (index in start until minOf(start + 7, deviceCount)) {
                    Box(Modifier.size(if (index == selectedIndex) 9.dp else 7.dp).background(if (index == selectedIndex) accent else Color(0xFFB9B1A3), CircleShape))
                }
            }
        }
        if (issue != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 90.dp, start = 16.dp, end = 16.dp).testTag("home-status"),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF7F6F1).copy(alpha = .96f),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                    issue.action?.let { TextButton(onClick = onResolveIssue) { Text(it) } }
                }
            }
        }
        if (showHint) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 100.dp).testTag("home-gesture-hint"),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF7F6F1).copy(alpha = .96f),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swipe to choose a device.\nTap the model to open it, or + to find a device.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismissHint, modifier = Modifier.testTag("dismiss-home-hint")) { Text("Got it") }
                }
            }
        }
    }
}
