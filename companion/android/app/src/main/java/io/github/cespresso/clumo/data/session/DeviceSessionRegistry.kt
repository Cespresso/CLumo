package io.github.cespresso.clumo.data.session

import android.content.Context
import android.os.SystemClock
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.data.ble.DeviceTransport
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal fun sessionStateMap(
    states: Map<String, StateFlow<DeviceSessionState>>,
): Flow<Map<String, DeviceSessionState>> {
    if (states.isEmpty()) return flowOf(emptyMap())
    val entries = states.entries.toList()
    return combine(entries.map { it.value }) { values ->
        entries.indices.associate { index -> entries[index].key to values[index] }
    }
}

/** Application-scoped owner of every active CLumo session. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceSessionRegistry(
    context: Context,
    private val repository: DeviceRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val transportFactory: (String, String?) -> DeviceTransport = { address, name ->
        DeviceConnection(context.applicationContext, address, name)
    },
    private val nowRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** Called when a known address comes back carrying a different firmware id. */
    private val onIdentitySuperseded: suspend (oldId: String, newId: String) -> Unit = { _, _ -> },
) {
    private val _sessions = MutableStateFlow<Map<String, DeviceSession>>(emptyMap())
    private val repositoryJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    val sessions = _sessions.asStateFlow()

    val states: StateFlow<Map<String, DeviceSessionState>> = sessions
        .flatMapLatest { current -> sessionStateMap(current.mapValues { it.value.state }) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun get(address: String): DeviceSession? = _sessions.value[address]

    fun connect(address: String, advertisedName: String? = null): DeviceSession {
        _sessions.value[address]?.let {
            it.connect()
            return it
        }
        val session = DeviceSession(
            transport = transportFactory(address, advertisedName),
            parentScope = scope,
            nowRealtime = nowRealtime,
        )
        _sessions.value = _sessions.value + (address to session)
        repositoryJobs[address] = wireRepositoryUpsert(session)
        session.connect()
        return session
    }

    fun disconnect(address: String) {
        val session = _sessions.value[address] ?: return
        repositoryJobs.remove(address)?.cancel()
        session.dispose()
        _sessions.value = _sessions.value - address
    }

    fun disconnectAll() {
        repositoryJobs.values.forEach { it.cancel() }
        repositoryJobs.clear()
        _sessions.value.values.forEach(DeviceSession::dispose)
        _sessions.value = emptyMap()
    }

    private fun wireRepositoryUpsert(session: DeviceSession) =
        scope.launch {
            session.deviceId.filterNotNull().collect { id ->
                // The settings move before the record is replaced; the other order leaves
                // them filed against an id nothing points at any more.
                repository.getByAddress(session.address)
                    ?.takeIf { it.id != id }
                    ?.let { onIdentitySuperseded(it.id, id) }
                repository.upsert(
                    Device(
                        id = id,
                        address = session.address,
                        name = session.deviceName.value ?: repository.get(id)?.name,
                        lastSeenAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

    fun connectionStateOf(device: Device): ConnectionState = states.value[device.address]?.link ?: ConnectionState.Disconnected
}
