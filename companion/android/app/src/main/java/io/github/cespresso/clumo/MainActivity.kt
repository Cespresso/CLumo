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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.service.DeviceHubService
import io.github.cespresso.clumo.ui.appearance.DeviceAppearanceScreen
import io.github.cespresso.clumo.ui.appearance.resolveAppearance
import io.github.cespresso.clumo.ui.device.DeviceScreen
import io.github.cespresso.clumo.ui.devices.DeviceListScreen
import io.github.cespresso.clumo.ui.editor.PatternEditorScreen
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Connections have to outlive this activity, so the hub runs as its own foreground
        // service. Nothing waits on it: the graph it drives is already in the container.
        DeviceHubService.start(this)
        val container = appContainer
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
    val appearances by preferences.deviceAppearances.collectAsState(initial = emptyMap())
    val primaryId by preferences.primaryDeviceId.collectAsState(initial = null)
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

    val backStack = remember(start) { mutableStateListOf<Screen>(start) }
    val current = backStack.last()

    fun push(screen: Screen) = backStack.add(screen)

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun replaceAll(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (current) {
        is Screen.OnboardingWelcome -> OnboardingWelcomeScreen(
            onStart = { push(Screen.OnboardingBluetooth) },
        )

        is Screen.OnboardingBluetooth -> OnboardingBluetoothScreen(
            preferences = container.preferences,
            onDone = { replaceAll(Screen.DeviceList) },
        )

        is Screen.DeviceList -> DeviceListScreen(
            registry = container.registry,
            repository = container.repository,
            preferences = container.preferences,
            patternRepository = container.patterns,
            scanner = container.scanner,
            onOpenDevice = { address -> push(Screen.Device(address)) },
            onOpenSettings = { push(Screen.Settings) },
        )

        is Screen.Device -> DeviceScreen(
            registry = container.registry,
            repository = container.repository,
            preferences = container.preferences,
            patternRepository = container.patterns,
            address = current.address,
            onBack = { pop() },
            onOpenSettings = { push(Screen.Settings) },
            onOpenAppearance = { deviceId -> push(Screen.Appearance(deviceId)) },
            onOpenEditor = { patternId ->
                push(Screen.Editor(address = current.address, patternId = patternId))
            },
        )

        is Screen.Editor -> PatternEditorScreen(
            registry = container.registry,
            repository = container.repository,
            preferences = container.preferences,
            patternRepository = container.patterns,
            address = current.address,
            patternId = current.patternId,
            onBack = { pop() },
        )

        is Screen.Appearance -> DeviceAppearanceScreen(
            repository = container.repository,
            preferences = container.preferences,
            deviceId = current.deviceId,
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
