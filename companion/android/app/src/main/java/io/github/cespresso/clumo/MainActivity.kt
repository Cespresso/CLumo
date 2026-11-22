package io.github.cespresso.clumo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.resolveAppearance
import io.github.cespresso.clumo.ui.appearance.AppearancePart
import io.github.cespresso.clumo.ui.appearance.ColorGroup
import io.github.cespresso.clumo.ui.appearance.DeviceAppearanceScreen
import io.github.cespresso.clumo.ui.appearance.DeviceAppearanceViewModel
import io.github.cespresso.clumo.ui.appearance.StandAppearanceEditor
import io.github.cespresso.clumo.ui.appearance.defaultStandAppearance
import io.github.cespresso.clumo.ui.appearance.editableParts
import io.github.cespresso.clumo.ui.appearance.modelRoles
import io.github.cespresso.clumo.ui.appearance.spatialLabel
import io.github.cespresso.clumo.ui.appearance.standThemes
import io.github.cespresso.clumo.ui.components.product.FloorSettingsState
import io.github.cespresso.clumo.ui.components.product.GalleryDevice
import io.github.cespresso.clumo.ui.components.product.MatrixPage
import io.github.cespresso.clumo.ui.components.product.ProductDisplayState
import io.github.cespresso.clumo.ui.components.product.ProductScene
import io.github.cespresso.clumo.ui.components.product.StandPart
import io.github.cespresso.clumo.ui.components.product.isGalleryAction
import io.github.cespresso.clumo.ui.components.product.selectedGalleryDevice
import io.github.cespresso.clumo.ui.device.DeviceScreen
import io.github.cespresso.clumo.ui.device.DeviceViewModel
import io.github.cespresso.clumo.ui.devices.DeviceListViewModel
import io.github.cespresso.clumo.ui.devices.FindDevices3DScreen
import io.github.cespresso.clumo.ui.devices.FindSceneActions
import io.github.cespresso.clumo.ui.devices.FindSetupScreen
import io.github.cespresso.clumo.ui.devices.Home3DOverlay
import io.github.cespresso.clumo.ui.devices.KnownDeviceCardState
import io.github.cespresso.clumo.ui.devices.SetupPage
import io.github.cespresso.clumo.ui.devices.discoveryGallery
import io.github.cespresso.clumo.ui.devices.setupModel
import io.github.cespresso.clumo.ui.editor.PatternEditorScreen
import io.github.cespresso.clumo.ui.editor.PatternEditorViewModel
import io.github.cespresso.clumo.ui.settings.LicensesScreen
import io.github.cespresso.clumo.ui.settings.SettingsScreen
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App destinations; navigation is a simple in-memory back stack. */
sealed interface Screen {
    data class Setup(val page: SetupPage) : Screen
    data object DeviceList : Screen
    data object FindDevices : Screen
    data class Device(val address: String) : Screen
    data class Editor(val address: String?, val patternId: String?) : Screen
    data class Appearance(val deviceId: String) : Screen
    data object Settings : Screen
    data object Prototype3D : Screen
    data object Licenses : Screen
}

private class ScreenEntry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Keep device ViewModels and the navigation stack through Activity recreation. */
private class AppNavigationViewModel(start: Screen) : ViewModel() {
    val entries = mutableStateListOf<ScreenEntry>().apply {
        if (start is Screen.Setup) add(ScreenEntry(Screen.DeviceList))
        add(ScreenEntry(start))
    }

    override fun onCleared() {
        entries.forEach { it.viewModelStore.clear() }
    }
}

@Composable
private fun rememberAppNavigation(
    start: Screen,
    navigation: AppNavigationViewModel = viewModel(
        key = "app-navigation",
        factory = viewModelFactory { initializer { AppNavigationViewModel(start) } },
    ),
): AppNavigationViewModel = navigation

private fun AppContainer.deviceViewModelFactory(address: String): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceViewModel(address, registry, preferences, patterns, repository)
        }
    }

private fun AppContainer.deviceListViewModelFactory(): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceListViewModel(registry, repository, preferences, scanner, defaultStandAppearance)
        }
    }

private fun AppContainer.patternEditorViewModelFactory(
    address: String?,
    patternId: String?,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            PatternEditorViewModel(address, patternId, registry, repository, preferences, patterns)
        }
    }

private fun AppContainer.deviceAppearanceViewModelFactory(
    deviceId: String,
    initialAppearance: DeviceAppearance,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceAppearanceViewModel(
                initialAppearance = initialAppearance,
                deviceId = deviceId,
                fallbackName = repository.get(deviceId)?.fallbackName,
                kind = repository.get(deviceId)?.kind ?: io.github.cespresso.clumo.domain.DeviceKind.CLUMO,
                preferences = preferences,
            )
        }
    }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = appContainer
        // Whatever this activity changes about a device has to reach the home screen too.
        container.widgetPublisher
        setContent {
            ClumoTheme(appearance = primaryDeviceAppearance(container)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClumoColors.Background),
                ) {
                    AppRoot(container, startPrototype = BuildConfig.DEBUG && intent.getBooleanExtra("prototype3d", false))
                }
            }
        }
    }
}

/** DEFAULT until preferences load. */
@Composable
private fun primaryDeviceAppearance(container: AppContainer): DeviceAppearance {
    val preferences = container.preferences
    val devices by container.repository.devices.collectAsStateWithLifecycle()
    val appearances by preferences.deviceAppearances.collectAsStateWithLifecycle(emptyMap())
    val primaryId by preferences.primaryDeviceId.collectAsStateWithLifecycle(null)
    if (devices.firstOrNull { it.id == primaryId }?.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) return DeviceAppearance.DEFAULT
    return resolveAppearance(primaryId, appearances)
}

@Composable
private fun AppRoot(
    container: AppContainer,
    startPrototype: Boolean = false,
    galleryViewModel: DeviceListViewModel = viewModel(key = "shared-gallery", factory = container.deviceListViewModelFactory()),
) {
    val scope = rememberCoroutineScope()
    val homeHintSeen by container.preferences.home3DHintSeen.collectAsStateWithLifecycle(initialValue = true)
    var selectedGalleryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFindId by rememberSaveable { mutableStateOf<String?>(null) }
    var findStarted by rememberSaveable { mutableStateOf(false) }
    val findActions = remember { FindSceneActions() }
    // null while the onboarding flag is loading, then the start destination.
    val startScreen by produceState<Screen?>(initialValue = null, container) {
        val done = container.preferences.onboardingDone.first()
        value = if (startPrototype) {
            Screen.Prototype3D
        } else if (done) {
            Screen.DeviceList
        } else {
            Screen.Setup(SetupPage.Welcome)
        }
    }
    val start = startScreen ?: return

    val navigation = rememberAppNavigation(start)
    val backStack = navigation.entries
    val currentEntry = backStack.last()
    val current = currentEntry.screen

    fun push(screen: Screen) = backStack.add(ScreenEntry(screen))

    fun openDevice(address: String) {
        selectedGalleryId = container.repository.getByAddress(address)?.id
        push(Screen.Device(address))
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex).viewModelStore.clear()
        }
    }

    fun replaceAll(screen: Screen) {
        backStack.forEach { it.viewModelStore.clear() }
        backStack.clear()
        backStack.add(ScreenEntry(screen))
    }

    fun beginSearch() {
        findStarted = false
        selectedFindId = null
        if (backStack.any { it.screen == Screen.FindDevices }) {
            while (backStack.last().screen != Screen.FindDevices) pop()
        } else {
            push(Screen.FindDevices)
        }
    }
    fun leaveSetup() {
        if (current == Screen.Setup(SetupPage.Welcome)) scope.launch { container.preferences.setOnboardingDone() }
        pop()
    }
    // All normal 3D destinations share this owner, including while a 2D settings sheet is open.
    val sceneEntry = backStack.lastOrNull { it.screen is Screen.DeviceList || it.screen is Screen.Device || it.screen is Screen.FindDevices || it.screen is Screen.Setup }
    val sceneScreen = sceneEntry?.screen
    val galleryState = galleryViewModel.uiState.collectAsStateWithLifecycle().value
    val deviceViewModel: DeviceViewModel? = if (sceneScreen is Screen.Device) {
        viewModel(viewModelStoreOwner = requireNotNull(sceneEntry), key = "device:${sceneScreen.address}", factory = container.deviceViewModelFactory(sceneScreen.address))
    } else {
        null
    }
    val floorSettings = rememberSaveable((sceneScreen as? Screen.Device)?.address, saver = FloorSettingsState.Saver) {
        FloorSettingsState(floorUi = false)
    }
    val selectedStandPart = floorSettings.destination.part
    var partBusy by remember { mutableStateOf(false) }
    var matrixPage by rememberSaveable((sceneScreen as? Screen.Device)?.address) { mutableStateOf(MatrixPage.Overview) }
    fun backFromDevice() {
        if (partBusy) return
        if (matrixPage != MatrixPage.Overview) {
            matrixPage = MatrixPage.Overview
        } else if (!floorSettings.navigateBack()) {
            pop()
        }
    }
    fun selectStandPart(part: StandPart?) {
        if (partBusy) return
        if (part == null) floorSettings.navigateBack() else floorSettings.selectPart(part)
    }
    BackHandler(enabled = backStack.size > 1) {
        if (current is Screen.Device) {
            backFromDevice()
        } else if (current is Screen.Setup) {
            leaveSetup()
        } else {
            pop()
        }
    }

    val operationState = deviceViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var standbyPreview by remember(sceneEntry) { mutableStateOf<Long?>(null) }
    val galleryDevices = galleryState.knownDevices.map { card ->
        card.toGalleryDevice().let { device ->
            if (operationState?.stableId == device.id && standbyPreview != null) {
                device.copy(display = device.display?.copy(bits = requireNotNull(standbyPreview)))
            } else if (operationState?.stableId == device.id && device.kind != io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) {
                device.copy(display = ProductDisplayState(operationState.appearance, operationState.link, operationState.mirrorBits, operationState.brightnessLevel))
            } else {
                device
            }
        }
    }
    val setup = (sceneScreen as? Screen.Setup)?.page
    val guideModel = if (setup != null) setupModel(setup, remember { standThemes[1].applyTo(DeviceAppearance.DEFAULT) }) else null
    val discoveries = discoveryGallery(galleryState)
    val sharedScene = sceneEntry != null && current !is Screen.Prototype3D
    LaunchedEffect(current) { if (current !is Screen.FindDevices) galleryViewModel.stopScan() }
    val findSelection = discoveries.firstOrNull { it.id == selectedFindId } ?: discoveries.firstOrNull()
    val selected = if (sceneScreen is Screen.Device) {
        galleryDevices.firstOrNull { it.address == sceneScreen.address }
    } else {
        selectedGalleryDevice(galleryDevices, selectedGalleryId)
    }
    val appearanceScreen = current as? Screen.Appearance
    val editingDevice = galleryDevices.firstOrNull { it.id == appearanceScreen?.deviceId }
    val spatialEdit = sharedScene && editingDevice != null
    var colorPart by rememberSaveable(appearanceScreen?.deviceId) { mutableStateOf(io.github.cespresso.clumo.ui.appearance.AppearancePart.Enclosure) }
    val standEdit = editingDevice?.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND
    val appearanceVm: DeviceAppearanceViewModel? = appearanceScreen?.let {
        viewModel(
            viewModelStoreOwner = currentEntry,
            key = "appearance:${it.deviceId}",
            factory = container.deviceAppearanceViewModelFactory(it.deviceId, editingDevice?.display?.appearance ?: DeviceAppearance.DEFAULT),
        )
    }
    val appearanceState = appearanceVm?.uiState?.collectAsStateWithLifecycle()?.value
    val standEditor = appearanceState?.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND
    var selectedColorPart by rememberSaveable(appearanceScreen?.deviceId) { mutableStateOf<AppearancePart?>(null) }
    var colorGroup by rememberSaveable(appearanceScreen?.deviceId) { mutableStateOf<ColorGroup?>(null) }
    var previewName by rememberSaveable(appearanceScreen?.deviceId) { mutableStateOf<String?>(null) }
    val themePreview = standThemes.firstOrNull { it.name == previewName }
    val editorAppearance = appearanceState?.appearance?.let { themePreview?.applyTo(it) ?: it }
    val editRoles = if (!standEditor) {
        colorPart.modelRoles()
    } else {
        selectedColorPart?.modelRoles()
            ?: editableParts(true).filter { colorGroup != null && editorAppearance?.assignments?.get(it.name) == colorGroup?.name }
                .flatMap { it.modelRoles() }.distinct()
    }
    fun editPart(part: AppearancePart) {
        previewName = null
        colorGroup = null
        selectedColorPart = part
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (sharedScene) {
            ProductScene(
                devices = if (spatialEdit) {
                    galleryDevices.map { item ->
                        if (item.id != editingDevice?.id) {
                            item
                        } else {
                            item.copy(
                                display = item.display?.copy(
                                    appearance = editorAppearance ?: item.display.appearance,
                                    connectionState = ConnectionState.Ready,
                                    volumeSegments = 0,
                                    bits = if ((if (standEditor) selectedColorPart else colorPart) in listOf(AppearancePart.Led, AppearancePart.Preview)) 0x00183C7E7EFF6600L else 0L,
                                    brightnessLevel = 15,
                                    outlinedRoles = editRoles,
                                ),
                            )
                        }
                    }
                } else {
                    galleryDevices
                },
                appearanceRoles = if (spatialEdit && !standEditor) colorPart.modelRoles() else emptyList(),
                appearanceLabel = if (spatialEdit && !standEditor) colorPart.spatialLabel() else "",
                onAppearanceRole = { role -> editableParts(standEdit).firstOrNull { role in it.modelRoles() }?.let { if (standEditor) editPart(it) else colorPart = it } },
                discoveryDevices = if (setup != null) listOfNotNull(guideModel) else discoveries,
                homeFraming = sceneScreen is Screen.DeviceList,
                floorActionsEnabled = false,
                selectedPart = selectedStandPart,
                matrixPage = matrixPage, onMatrixSelect = { matrixPage = it },
                floorSettings = floorSettings,
                onPartSelect = { selectStandPart(it) },
                partInputEnabled = !partBusy && floorSettings.closeToken == null,
                interactionEnabled = spatialEdit || current is Screen.DeviceList || current is Screen.FindDevices || current is Screen.Device,
                selectedId = if (setup != null) {
                    guideModel?.id.orEmpty()
                } else if (spatialEdit) {
                    requireNotNull(editingDevice).id
                } else if (sceneScreen is Screen.FindDevices) {
                    findSelection?.id.orEmpty()
                } else {
                    selected?.id.orEmpty()
                },
                onOpenDevice = {
                    if (current is Screen.FindDevices) {
                        selectedFindId = "find:$it"
                        findActions.openDevice(it)
                    } else {
                        openDevice(it)
                    }
                },
                onOpenSettings = { push(Screen.Settings) },
                onFindDevices = {
                    if (current is Screen.FindDevices) {
                        findActions.scan()
                    } else {
                        findStarted = false
                        selectedFindId = null
                        push(Screen.FindDevices)
                    }
                },
                onSelect = { if (current is Screen.FindDevices) selectedFindId = it else selectedGalleryId = it }, mode = 0,
                destination = if (spatialEdit) {
                    "appearance"
                } else {
                    when (sceneScreen) {
                        is Screen.Device -> "controls"
                        is Screen.Setup -> if (setup == SetupPage.Stand) "guide-stand" else "guide"
                        is Screen.FindDevices -> "find"
                        else -> "overview"
                    }
                },
                reset = 0,
                aspect = maxWidth.value / maxHeight.value, landscape = maxWidth > maxHeight,
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFCAC6BE)),
            )
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides currentEntry) {
            when (current) {
                is Screen.Setup -> FindSetupScreen(
                    current.page,
                    onPage = { push(Screen.Setup(it)) },
                    onSearch = ::beginSearch,
                    onBack = ::leaveSetup,
                )

                is Screen.DeviceList -> Home3DOverlay(
                    onOpenSettings = { push(Screen.Settings) },
                    onFindDevices = {
                        findStarted = false
                        selectedFindId = null
                        push(Screen.FindDevices)
                    },
                    deviceCount = galleryDevices.size,
                    selectedIndex = galleryDevices.indexOfFirst { it.id == selected?.id },
                    accent = androidx.compose.ui.graphics.Color((selected?.display?.appearance?.buttonBColor?.value ?: 0xFFB87858.toInt()) or 0xFF000000.toInt()),
                    showHint = !homeHintSeen,
                    onDismissHint = { scope.launch { container.preferences.dismissHome3DHint() } },
                )

                is Screen.FindDevices -> {
                    FindDevices3DScreen(
                        state = galleryState,
                        selected = findSelection,
                        onGuide = { push(Screen.Setup(SetupPage.Products)) },
                        onScan = { galleryViewModel.startScan() },
                        onStopScan = { galleryViewModel.stopScan() },
                        onConnect = { address, name -> galleryViewModel.connect(address, name) },
                        actions = findActions,
                        autoStartScan = !findStarted,
                        onScanStart = { findStarted = true },
                        onResetSelection = { selectedFindId = null },
                        onBack = { pop() },
                        onOpenDevice = { address ->
                            scope.launch { container.preferences.setOnboardingDone() }
                            openDevice(address)
                        },
                    )
                }

                is Screen.Device -> DeviceScreen(
                    onLeaveDevice = { pop() },
                    onStandbyPreview = { standbyPreview = it },
                    selectedPart = selectedStandPart,
                    matrixPage = matrixPage, onMatrixSelect = { matrixPage = it },
                    floorSettings = floorSettings,
                    onPartSelect = { selectStandPart(it) },
                    onPartBusyChange = { partBusy = it },
                    viewModel = requireNotNull(deviceViewModel),
                    onBack = { backFromDevice() },
                    onOpenSettings = { push(Screen.Settings) },
                    onOpenAppearance = { deviceId -> push(Screen.Appearance(deviceId)) },
                    onOpenEditor = { patternId ->
                        push(Screen.Editor(address = current.address, patternId = patternId))
                    },
                )

                is Screen.Editor -> PatternEditorScreen(
                    viewModel = viewModel(
                        key = "editor:${current.address}:${current.patternId}",
                        factory = container.patternEditorViewModelFactory(
                            address = current.address,
                            patternId = current.patternId,
                        ),
                    ),
                    onBack = { pop() },
                )

                is Screen.Appearance -> if (standEditor) {
                    StandAppearanceEditor(
                        vm = requireNotNull(appearanceVm), appearance = requireNotNull(editorAppearance),
                        spatial = spatialEdit, part = selectedColorPart, group = colorGroup, preview = themePreview,
                        selectPart = ::editPart, selectGroup = {
                            previewName = null
                            selectedColorPart = null
                            colorGroup = it
                        },
                        previewTheme = { previewName = it?.name },
                        back = {
                            if (selectedColorPart != null || colorGroup != null || previewName != null) {
                                selectedColorPart = null
                                colorGroup = null
                                previewName = null
                            } else {
                                pop()
                            }
                        },
                    )
                } else {
                    DeviceAppearanceScreen(
                        spatialPart = colorPart.takeIf { spatialEdit },
                        onPart = { colorPart = it },
                        viewModel = requireNotNull(appearanceVm),
                        onBack = { pop() },
                    )
                }

                is Screen.Settings -> SettingsScreen(
                    onBack = { pop() },
                    onOpenLicenses = { push(Screen.Licenses) },
                )

                is Screen.Prototype3D -> PrototypeDeviceRoute(
                    viewModel = viewModel(key = "prototype-devices", factory = container.deviceListViewModelFactory()),
                    onOpenDevice = { openDevice(it) },
                    onBack = { if (backStack.size > 1) pop() else replaceAll(Screen.DeviceList) },
                )

                is Screen.Licenses -> LicensesScreen(
                    onBack = { pop() },
                )
            }
        }
    }
}

private fun KnownDeviceCardState.toGalleryDevice() =
    GalleryDevice(
        id = device.id,
        name = name,
        kind = device.kind,
        address = device.address,
        primary = isPrimary,
        display = ProductDisplayState(appearance, connectionState, mirrorBits, brightnessLevel, volumeSegments),
        status = when (connectionState) {
            ConnectionState.Ready -> "接続済み"
            ConnectionState.Connected, ConnectionState.Synchronizing -> "同期中"
            ConnectionState.Connecting -> "接続中"
            ConnectionState.Reconnecting -> "再接続中"
            ConnectionState.Bonding -> "ペアリング中"
            ConnectionState.Disconnected -> "未接続"
            ConnectionState.Error -> "接続エラー"
        },
    )

@Composable
private fun PrototypeDeviceRoute(
    viewModel: DeviceListViewModel,
    onOpenDevice: (String) -> Unit,
    onBack: () -> Unit,
    integrated: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onManageDevices: () -> Unit = {},
    selectedId: String? = null,
    onSelectionChange: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.cespresso.clumo.ui.prototype.Prototype3DScreen(
        devices = state.knownDevices.map { it.toGalleryDevice() },
        integrated = integrated,
        onOpenSettings = onOpenSettings,
        onManageDevices = onManageDevices,
        initialSelectedId = selectedId,
        onSelectionChange = onSelectionChange,
        onOpenDevice = onOpenDevice,
        onBack = onBack,
    )
}
