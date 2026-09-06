package io.github.cespresso.clumo.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.DeviceScanner
import io.github.cespresso.clumo.data.session.DeviceSession
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceNaming
import io.github.cespresso.clumo.domain.DeviceSessionState
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.ScanEvent
import io.github.cespresso.clumo.domain.ScanFailure
import io.github.cespresso.clumo.domain.mirrorBitsFor
import io.github.cespresso.clumo.domain.resolveAppearance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class DeviceCardLiveState(
    val session: DeviceSessionState,
    val visualizerColumns: IntArray = IntArray(0),
    val visualizerActive: Boolean = false,
)

data class KnownDeviceCardState(
    val device: Device,
    val name: String,
    val appearance: DeviceAppearance,
    val connectionState: ConnectionState,
    val mirrorBits: Long,
    val litAlpha: Float,
    val isPrimary: Boolean,
)

data class DeviceScanState(
    val scanning: Boolean = false,
    val advertisements: Map<String, DeviceAdvertisement> = emptyMap(),
    val failure: ScanFailure? = null,
    val finishedEmpty: Boolean = false,
)

internal fun reduceScanEvent(state: DeviceScanState, event: ScanEvent): DeviceScanState =
    when (event) {
        is ScanEvent.DeviceFound -> state.copy(
            advertisements = state.advertisements +
                (event.advertisement.address to event.advertisement),
        )
        is ScanEvent.Failed -> state.copy(scanning = false, failure = event.reason)
    }

internal fun buildKnownDeviceCards(
    devices: List<Device>,
    liveStates: Map<String, DeviceCardLiveState>,
    aliases: Map<String, String>,
    appearances: Map<String, DeviceAppearance>,
    primaryDeviceId: String?,
    patterns: List<Pattern>,
    appliedPatternIds: Map<String, String>,
): List<KnownDeviceCardState> = devices.map { device ->
    val live = liveStates[device.address]
    val state = live?.session ?: DeviceSessionState(ConnectionState.Disconnected)
    val observed = state.observed
    val selectedPattern = patterns.firstOrNull { it.id == appliedPatternIds[device.id] }
    val ready = state.link == ConnectionState.Ready
    KnownDeviceCardState(
        device = device,
        name = DeviceNaming.displayName(
            deviceId = device.id,
            aliases = aliases,
            fallbackName = device.fallbackName,
        ),
        appearance = resolveAppearance(device.id, appearances),
        connectionState = state.link,
        mirrorBits = if (!ready) FaceBits.EMPTY else mirrorBitsFor(
            mode = observed?.mode,
            pomodoro = observed?.pomodoro,
            timer = observed?.timer,
            selectedPatternBits = selectedPattern?.bits,
            committedFrame = state.effectiveCommittedFrame,
            columns = live?.visualizerColumns ?: IntArray(0),
            visualizerActive = live?.visualizerActive == true,
            timerBlinkOn = true,
        ),
        litAlpha = Brightness.litAlpha(
            state.effectiveBrightnessLevel ?: Brightness.MAX_LEVEL,
        ),
        isPrimary = device.id == primaryDeviceId,
    )
}

data class DeviceListUiState(
    val knownDevices: List<KnownDeviceCardState> = emptyList(),
    val foundDevices: List<DeviceAdvertisement> = emptyList(),
    val scanning: Boolean = false,
    val scanFailure: ScanFailure? = null,
    val scanFinishedEmpty: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModel(
    private val registry: DeviceSessionRegistry,
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
    patterns: PatternRepository,
    private val scanner: DeviceScanner,
) : ViewModel() {
    private val scanState = MutableStateFlow(DeviceScanState())
    private var scanJob: Job? = null

    private val identity = combine(
        repository.devices,
        preferences.aliases,
        preferences.deviceAppearances,
        preferences.primaryDeviceId,
    ) { devices, aliases, appearances, primaryId ->
        Identity(devices, aliases, appearances, primaryId)
    }
    private val library = combine(patterns.patterns, patterns.appliedPatternIds) { list, applied ->
        Library(list, applied)
    }
    private val liveStates = registry.sessions.flatMapLatest(::cardLiveStateMap)

    val uiState = combine(identity, library, liveStates, scanState) {
            identity, library, liveStates, scan ->
        val knownAddresses = identity.devices.mapTo(mutableSetOf()) { it.address }
        DeviceListUiState(
            knownDevices = buildKnownDeviceCards(
                devices = identity.devices,
                liveStates = liveStates,
                aliases = identity.aliases,
                appearances = identity.appearances,
                primaryDeviceId = identity.primaryDeviceId,
                patterns = library.patterns,
                appliedPatternIds = library.appliedPatternIds,
            ),
            foundDevices = scan.advertisements.values
                .filter { it.address !in knownAddresses }
                .sortedByDescending { it.rssi },
            scanning = scan.scanning,
            scanFailure = scan.failure,
            scanFinishedEmpty = scan.finishedEmpty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceListUiState())

    fun startScan() {
        scanJob?.cancel()
        scanState.value = DeviceScanState(scanning = true)
        scanJob = viewModelScope.launch {
            val completed = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                scanner.scan().collect { event ->
                    scanState.value = reduceScanEvent(scanState.value, event)
                }
                true
            }
            val current = scanState.value
            if (current.scanning || completed == null) {
                scanState.value = current.copy(
                    scanning = false,
                    finishedEmpty = current.advertisements.isEmpty() && current.failure == null,
                )
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanState.value = scanState.value.copy(scanning = false)
    }

    fun connect(address: String, name: String?) {
        stopScan()
        registry.connect(address, name)
    }

    fun togglePrimary(deviceId: String) {
        val current = uiState.value.knownDevices.firstOrNull { it.device.id == deviceId }
            ?: return
        viewModelScope.launch {
            preferences.setPrimaryDeviceId(if (current.isPrimary) null else deviceId)
        }
    }

    private fun cardLiveStateMap(
        sessions: Map<String, DeviceSession>,
    ): Flow<Map<String, DeviceCardLiveState>> {
        if (sessions.isEmpty()) return flowOf(emptyMap())
        val entries = sessions.entries.toList()
        return combine(entries.map { (_, session) ->
            combine(
                session.state,
                session.visualizerColumns,
                session.visualizerActive,
            ) { state, columns, active -> DeviceCardLiveState(state, columns, active) }
        }) { values ->
            entries.indices.associate { index -> entries[index].key to values[index] }
        }
    }

    private data class Identity(
        val devices: List<Device>,
        val aliases: Map<String, String>,
        val appearances: Map<String, DeviceAppearance>,
        val primaryDeviceId: String?,
    )
    private data class Library(
        val patterns: List<Pattern>,
        val appliedPatternIds: Map<String, String>,
    )

    private companion object {
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}
