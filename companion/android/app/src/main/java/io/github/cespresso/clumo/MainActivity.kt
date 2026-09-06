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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.resolveAppearance
import io.github.cespresso.clumo.ui.appearance.DeviceAppearanceScreen
import io.github.cespresso.clumo.ui.appearance.DeviceAppearanceViewModel
import io.github.cespresso.clumo.ui.device.DeviceScreen
import io.github.cespresso.clumo.ui.device.DeviceViewModel
import io.github.cespresso.clumo.ui.devices.DeviceListScreen
import io.github.cespresso.clumo.ui.devices.DeviceListViewModel
import io.github.cespresso.clumo.ui.editor.PatternEditorScreen
import io.github.cespresso.clumo.ui.editor.PatternEditorViewModel
import io.github.cespresso.clumo.ui.onboarding.OnboardingBluetoothScreen
import io.github.cespresso.clumo.ui.onboarding.OnboardingWelcomeScreen
import io.github.cespresso.clumo.ui.settings.LicensesScreen
import io.github.cespresso.clumo.ui.settings.SettingsScreen
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import kotlinx.coroutines.flow.first

/** App destinations; navigation is a simple in-memory back stack. */
sealed interface Screen {
    data object OnboardingWelcome : Screen
    data object OnboardingBluetooth : Screen
    data object DeviceList : Screen
    data class Device(val address: String) : Screen
    data class Editor(val address: String?, val patternId: String?) : Screen
    data class Appearance(val deviceId: String) : Screen
    data object Settings : Screen
    data object Licenses : Screen
}

private class ScreenEntry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private fun AppContainer.deviceViewModelFactory(address: String): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceViewModel(address, registry, preferences, patterns, repository)
        }
    }

private fun AppContainer.deviceListViewModelFactory(): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceListViewModel(registry, repository, preferences, scanner)
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
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DeviceAppearanceViewModel(
                deviceId = deviceId,
                fallbackName = repository.get(deviceId)?.fallbackName,
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
            ClumoTheme(appearance = primaryDeviceAppearance(container.preferences)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClumoColors.Background),
                ) {
                    AppRoot(container)
                }
            }
        }
    }
}

/** DEFAULT until preferences load. */
@Composable
private fun primaryDeviceAppearance(preferences: AppPreferences): DeviceAppearance {
    val appearances by preferences.deviceAppearances.collectAsStateWithLifecycle(emptyMap())
    val primaryId by preferences.primaryDeviceId.collectAsStateWithLifecycle(null)
    return resolveAppearance(primaryId, appearances)
}

@Composable
private fun AppRoot(container: AppContainer) {
    // null while the onboarding flag is loading, then the start destination.
    val startScreen by produceState<Screen?>(initialValue = null, container) {
        val done = container.preferences.onboardingDone.first()
        value = if (done) Screen.DeviceList else Screen.OnboardingWelcome
    }
    val start = startScreen ?: return

    val backStack = remember(start) { mutableStateListOf(ScreenEntry(start)) }
    val currentEntry = backStack.last()
    val current = currentEntry.screen

    DisposableEffect(backStack) {
        onDispose { backStack.forEach { it.viewModelStore.clear() } }
    }

    fun push(screen: Screen) = backStack.add(ScreenEntry(screen))

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

    BackHandler(enabled = backStack.size > 1) { pop() }

    CompositionLocalProvider(LocalViewModelStoreOwner provides currentEntry) {
        when (current) {
            is Screen.OnboardingWelcome -> OnboardingWelcomeScreen(
                onStart = { push(Screen.OnboardingBluetooth) },
            )

            is Screen.OnboardingBluetooth -> OnboardingBluetoothScreen(
                preferences = container.preferences,
                onDone = { replaceAll(Screen.DeviceList) },
            )

            is Screen.DeviceList -> DeviceListScreen(
                viewModel = viewModel(
                    key = "device-list",
                    factory = container.deviceListViewModelFactory(),
                ),
                onOpenDevice = { address -> push(Screen.Device(address)) },
                onOpenSettings = { push(Screen.Settings) },
            )

            is Screen.Device -> DeviceScreen(
                viewModel = viewModel(
                    key = "device:${current.address}",
                    factory = container.deviceViewModelFactory(current.address),
                ),
                onBack = { pop() },
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

            is Screen.Appearance -> DeviceAppearanceScreen(
                viewModel = viewModel(
                    key = "appearance:${current.deviceId}",
                    factory = container.deviceAppearanceViewModelFactory(current.deviceId),
                ),
                onBack = { pop() },
            )

            is Screen.Settings -> SettingsScreen(
                onBack = { pop() },
                onOpenLicenses = { push(Screen.Licenses) },
            )

            is Screen.Licenses -> LicensesScreen(
                onBack = { pop() },
            )
        }
    }
}
