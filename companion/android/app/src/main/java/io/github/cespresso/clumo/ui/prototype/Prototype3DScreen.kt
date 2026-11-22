package io.github.cespresso.clumo.ui.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.ui.components.product.GalleryDevice
import io.github.cespresso.clumo.ui.components.product.ProductScene
import io.github.cespresso.clumo.ui.components.product.demoGalleryDevices
import io.github.cespresso.clumo.ui.components.product.isGalleryAction
import io.github.cespresso.clumo.ui.components.product.selectedGalleryDevice

/** Full-screen scene; navigation and device UI are overlays on the same renderer. */
@Composable
fun Prototype3DScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    devices: List<GalleryDevice> = emptyList(),
    onOpenDevice: (String) -> Unit = {},
    integrated: Boolean = false,
    renderScene: Boolean = true,
    onOpenSettings: () -> Unit = {},
    onManageDevices: () -> Unit = {},
    initialSelectedId: String? = null,
    onSelectionChange: (String) -> Unit = {},
) {
    var demo by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var reset by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }
    val items = if (demo) demoGalleryDevices else devices
    val selected = if (!renderScene && isGalleryAction(initialSelectedId)) null else selectedGalleryDevice(items, if (renderScene) selectedId else initialSelectedId)
    val notifySelection by rememberUpdatedState(onSelectionChange)
    val selectedIndex = items.indexOf(selected)
    val stand = selected?.kind == DeviceKind.HEADPHONE_STAND
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val detail = stand && entry?.destination?.route == "controls"
    fun showOverview() {
        navigation.popBackStack("overview", inclusive = false)
    }
    fun select(id: String) {
        showOverview()
        selectedId = id
        if (!renderScene) notifySelection(id)
    }
    LaunchedEffect(selected?.id, stand) {
        // Keep the restored selection while the device list is re-subscribing after rotation.
        if (selected != null) {
            if (selectedId != selected.id || !stand) showOverview()
            selectedId = selected.id
            notifySelection(selected.id)
        }
    }
    BackHandler(enabled = detail) { showOverview() }
    BoxWithConstraints(modifier.fillMaxSize().background(if (renderScene) Color(0xFFCAC6BE) else Color.Transparent).testTag("gallery-root")) {
        val landscape = maxWidth > maxHeight
        val aspect = maxWidth.value / maxHeight.value
        val panelWidth = if (landscape) maxWidth * .43f else maxWidth - 32.dp
        val panelHeight = if (landscape) (maxHeight - 200.dp).coerceAtLeast(96.dp) else maxHeight * (if (detail) .36f else .30f)
        if (renderScene && selected != null) {
            key(mode, items.map { Triple(it.id, it.kind, it.asset(mode)) }) {
                ProductScene(
                    devices = items, selectedId = selected.id, onSelect = { select(it) },
                    destination = if (detail) "controls" else "overview", reset = reset, mode = mode,
                    aspect = aspect, landscape = landscape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(16.dp)
                .background(Color(0xFFCAC6BE).copy(alpha = .92f), RoundedCornerShape(28.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .9f)) {
                TextButton(onClick = { if (detail) showOverview() else onBack() }, modifier = Modifier.semantics { contentDescription = "戻る" }) { Text("‹") }
            }
            Column(Modifier.weight(1f)) {
                Text(if (detail) "操作部の説明" else "デバイス", style = MaterialTheme.typography.titleLarge)
                Text(if (demo) "デモ · 実際の接続機器ではありません" else "登録済み ${devices.size}台", style = MaterialTheme.typography.labelMedium)
            }
            if (!integrated || renderScene) {
                Box {
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .9f)) {
                        TextButton(onClick = { if (integrated) onOpenSettings() else options = true }, modifier = Modifier.testTag("gallery-options")) { Text(if (integrated) "設定" else "表示") }
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text("登録済み · ${devices.size}台") }, onClick = {
                            showOverview()
                            demo = false
                            selectedId = null
                            options = false
                        }, modifier = Modifier.testTag("gallery-real"))
                        DropdownMenuItem(text = { Text("デモ表示") }, onClick = {
                            showOverview()
                            demo = true
                            selectedId = null
                            options = false
                        }, modifier = Modifier.testTag("gallery-demo"))
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("光を焼き込み${if (mode == 0) " ✓" else ""}") }, onClick = {
                            mode = 0
                            options = false
                        })
                        DropdownMenuItem(text = { Text("立体的な陰影${if (mode == 1) " ✓" else ""}") }, onClick = {
                            mode = 1
                            options = false
                        })
                        DropdownMenuItem(text = { Text("構図を戻す") }, onClick = {
                            reset++
                            options = false
                        }, modifier = Modifier.testTag("reset-camera"))
                    }
                }
            }
        }
        if (integrated && !renderScene) {
            Text(
                "左右にスワイプ · モデルやアイコンをタップ",
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Surface(
                modifier = Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                    .navigationBarsPadding().padding(16.dp).width(panelWidth).heightIn(max = panelHeight)
                    .testTag("gallery-panel"),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = .95f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    Modifier.then(if (integrated) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selected == null) {
                        Text("デバイスを追加してはじめる", style = MaterialTheme.typography.titleMedium)
                        Text("デバイス一覧で接続すると、ここに表示されます。", modifier = Modifier.testTag("gallery-empty"))
                        if (integrated) {
                            TextButton(onClick = onManageDevices) { Text("デバイスを追加") }
                        } else {
                            TextButton(onClick = { demo = true }, modifier = Modifier.testTag("empty-demo")) { Text("デモで試す") }
                        }
                    } else {
                        if (!detail) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { select(items[selectedIndex - 1].id) },
                                    enabled = selectedIndex > 0,
                                    modifier = Modifier.testTag("previous-device").semantics { contentDescription = "前のデバイス" },
                                ) { Text("‹") }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { chooser = true }, modifier = Modifier.testTag("device-chooser")) {
                                        Text("${selectedIndex + 1} / ${items.size} 台 · 一覧")
                                    }
                                    DropdownMenu(expanded = chooser, onDismissRequest = { chooser = false }) {
                                        items.forEach { device ->
                                            DropdownMenuItem(text = { Text(device.name + " · " + device.status) }, onClick = {
                                                select(device.id)
                                                chooser = false
                                            }, modifier = Modifier.testTag("device-${device.id}"))
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { select(items[selectedIndex + 1].id) },
                                    enabled = selectedIndex < items.lastIndex,
                                    modifier = Modifier.testTag("next-device").semantics { contentDescription = "次のデバイス" },
                                ) { Text("›") }
                            }
                        }
                        Text(
                            selected.name + " · " + selected.status,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("gallery-selection"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (stand && !integrated) {
                                if (detail) {
                                    TextButton(onClick = { showOverview() }, modifier = Modifier.testTag("mode-overview")) { Text("全景に戻る") }
                                } else {
                                    OutlinedButton(onClick = { navigation.navigate("controls") { launchSingleTop = true } }, modifier = Modifier.testTag("mode-controls")) { Text("操作部の説明") }
                                }
                            }
                            if (selected.address != null && renderScene) {
                                Button(onClick = { onOpenDevice(selected.address) }, modifier = Modifier.testTag("open-selected-device")) { Text("操作する") }
                            }
                            if (integrated && renderScene) {
                                TextButton(onClick = onManageDevices) { Text("追加・管理") }
                            }
                        }
                        NavHost(
                            navController = navigation,
                            startDestination = "overview",
                            modifier = Modifier.weight(1f, fill = false),
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None },
                        ) {
                            composable("overview") {
                                Text(if (integrated) "左右にスワイプして切り替え · モデルをタップして操作" else "左右にスワイプして切り替え\n機器のタップでも選択 · 色や点灯は表示例", style = MaterialTheme.typography.bodySmall)
                            }
                            composable("controls") {
                                Column(Modifier.verticalScroll(rememberScrollState()).testTag("guide-scroll")) { ControlsGuide() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("controls-guide")) {
        Text("操作と表示 · 初期割り当て")
        Text("モデルをドラッグで回転 · 2本指で拡大")
        Text("① 赤い8×8マトリクス：操作アニメーション・待機表示")
        Text("② 右上の3連LED：音量表示。Androidはアプリ、iPhoneはAMS経由")
        Text("③ 右下のオレンジLED：電源表示")
        Text("④ 左右のボタン：左は前へ、右は次へ")
        Text("⑤ ノブ：回して音量、1回押して再生・一時停止。2回で次へ、3回で前へ")
        Text("ノブを3秒：新規ペアリング受付。左右ボタンを同時に3秒：Wi-Fi設定の切り替え")
        Text("点灯は説明用の表示例です。実機の状態とは連動しません。")
    }
}
