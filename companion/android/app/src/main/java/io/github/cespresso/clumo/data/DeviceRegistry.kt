package io.github.cespresso.clumo.data

import android.content.Context
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * In-memory registry of active [DeviceConnection]s keyed by MAC address.
 *
 * The registry is the sole owner of connections' lifecycle:
 * - [connect] creates (or reuses) a connection for a MAC and starts its GATT handshake.
 * - When the connection reads the firmware's device id, the registry upserts it into
 *   [DeviceRepository] so the device is persisted as "known".
 * - [disconnect] tears down the connection and removes it from the map.
 */
class DeviceRegistry(
    private val context: Context,
    private val repository: DeviceRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connections = MutableStateFlow<Map<String, DeviceConnection>>(emptyMap())
    val connections = _connections.asStateFlow()

    /** Look up an active connection by MAC. Returns null if not connected. */
    fun get(address: String): DeviceConnection? = _connections.value[address]

    /**
     * Connect to [address] if not already connected. [advertisedName] is used
     * until the firmware's device-id read completes.
     */
    fun connect(address: String, advertisedName: String? = null): DeviceConnection {
        _connections.value[address]?.let {
            it.connect()
            return it
        }

        val connection = DeviceConnection(context, address, advertisedName)
        _connections.value = _connections.value + (address to connection)
        wireRepositoryUpsert(connection)
        connection.connect()
        return connection
    }

    fun disconnect(address: String) {
        val conn = _connections.value[address] ?: return
        conn.dispose()
        _connections.value = _connections.value - address
    }

    fun disconnectAll() {
        _connections.value.values.forEach { it.dispose() }
        _connections.value = emptyMap()
    }

    /**
     * Observe each connection's device id and upsert into the repository on
     * first read (and on subsequent name/address changes).
     */
    private fun wireRepositoryUpsert(connection: DeviceConnection): Job = scope.launch {
        connection.deviceId.filterNotNull().collect { id ->
            repository.upsert(
                Device(
                    id = id,
                    address = connection.address,
                    name = connection.deviceName.value ?: repository.get(id)?.name,
                    lastSeenAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun connectionStateOf(device: Device): ConnectionState =
        _connections.value[device.address]?.connectionState?.value ?: ConnectionState.Disconnected
}
