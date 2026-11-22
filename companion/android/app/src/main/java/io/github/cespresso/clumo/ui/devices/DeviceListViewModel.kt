package io.github.cespresso.clumo.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.ble.DeviceScanner
import io.github.cespresso.clumo.data.session.DeviceSession
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.domain.Brightness
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.DeviceNaming
import io.github.cespresso.clumo.domain.DeviceSessionState
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.ScanEvent
import io.github.cespresso.clumo.domain.ScanFailure
import io.github.cespresso.clumo.domain.mirrorBitsFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    val standbyBits: Long? = null,
    val volumeSegments: Int = 0,
)

data class KnownDeviceCardState(
    val device: Device,
    val name: String,
    val appearance: DeviceAppearance,
    val connectionState: ConnectionState,
    val mirrorBits: Long,
    val litAlpha: Float,
    val isPrimary: Boolean,
    val volumeSegments: Int = 0,
    val brightnessLevel: Int = Brightness.MAX_LEVEL,
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
    defaultStandAppearance: DeviceAppearance,
): List<KnownDeviceCardState> =
    devices.map { device ->
        val live = liveStates[device.address]
        val state = live?.session ?: DeviceSessionState(ConnectionState.Disconnected)
        val observed = state.observed
        val ready = state.link == ConnectionState.Ready
        KnownDeviceCardState(
            device = device,
            name = DeviceNaming.displayName(
                deviceId = device.id,
                aliases = aliases,
                fallbackName = device.fallbackName,
            ),
            appearance = appearances[device.id] ?: if (device.kind == DeviceKind.HEADPHONE_STAND) defaultStandAppearance else DeviceAppearance.DEFAULT,
            connectionState = state.link,
            mirrorBits = if (!ready) {
                FaceBits.EMPTY
            } else if (device.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) {
                if (live?.visualizerActive == true) {
                    FaceBits.fromColumns(live.visualizerColumns)
                } else {
                    live?.standbyBits ?: FaceBits.EMPTY
                }
            } else {
                mirrorBitsFor(
                    mode = observed?.mode,
                    pomodoro = observed?.pomodoro,
                    timer = observed?.timer,
                    committedFrame = state.effectiveCommittedFrame,
                    columns = live?.visualizerColumns ?: IntArray(0),
                    visualizerActive = live?.visualizerActive == true,
                    timerBlinkOn = true,
                )
            },
            brightnessLevel = state.effectiveBrightnessLevel ?: Brightness.MAX_LEVEL,
            litAlpha = Brightness.litAlpha(
                state.effectiveBrightnessLevel ?: Brightness.MAX_LEVEL,
            ),
            volumeSegments = live?.volumeSegments ?: 0,
            isPrimary = device.id == primaryDeviceId,
        )
    }

data class DeviceListUiState(
    val knownDevices: List<KnownDeviceCardState> = emptyList(),
    val foundDevices: List<DeviceAdvertisement> = emptyList(),
    val scanning: Boolean = false,
    val scanFailure: ScanFailure? = null,
    val scanFinishedEmpty: Boolean = false,
    val discoveredDevices: List<DeviceAdvertisement> = emptyList(),
    val connectionStates: Map<String, ConnectionState> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModel(
    private val registry: DeviceSessionRegistry,
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
    private val scanner: DeviceScanner,
    private val defaultStandAppearance: DeviceAppearance,
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
    private val liveStates = registry.sessions.flatMapLatest(::cardLiveStateMap)

    val uiState = combine(identity, liveStates, scanState) { identity, liveStates, scan ->
        val knownAddresses = identity.devices.mapTo(mutableSetOf()) { it.address }
        DeviceListUiState(
            knownDevices = buildKnownDeviceCards(
                devices = identity.devices,
                liveStates = liveStates,
                aliases = identity.aliases,
                appearances = identity.appearances,
                primaryDeviceId = identity.primaryDeviceId,
                defaultStandAppearance = defaultStandAppearance,
            ),
            foundDevices = scan.advertisements.values
                .filter { it.address !in knownAddresses }
                .sortedByDescending { it.rssi },
            discoveredDevices = scan.advertisements.values.toList(),
            connectionStates = liveStates.mapValues { it.value.session.link },
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

    /** The device screen's own forget action, reachable from the list. */
    fun removeDevice(deviceId: String) {
        val device = repository.get(deviceId) ?: return
        registry.disconnect(device.address)
        repository.remove(deviceId)
        viewModelScope.launch(NonCancellable) { preferences.clearDeviceSettings(deviceId) }
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
        return combine(
            entries.map { (_, session) ->
                combine(
                    session.state,
                    session.visualizerColumns,
                    session.visualizerActive,
                    session.standbyBits,
                    session.volumeSegments,
                ) { state, columns, active, standby, volume -> DeviceCardLiveState(state, columns, active, standby, volume) }
            },
        ) { values ->
            entries.indices.associate { index -> entries[index].key to values[index] }
        }
    }

    private data class Identity(
        val devices: List<Device>,
        val aliases: Map<String, String>,
        val appearances: Map<String, DeviceAppearance>,
        val primaryDeviceId: String?,
    )

    private companion object {
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}
