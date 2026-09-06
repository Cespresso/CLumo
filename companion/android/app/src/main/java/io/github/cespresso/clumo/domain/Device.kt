package io.github.cespresso.clumo.domain

/**
 * Persistent identity of a CLumo device.
 *
 * - [id] is the firmware-generated UUID persisted in NVS. Stable across MAC changes.
 * - [address] is the most recently observed MAC; used as the BLE reconnect target.
 * - [name] is the most recently advertised device name (e.g. "CLumo-3F2A").
 *
 * User-visible aliases are stored separately (DataStore, keyed by [id]).
 */
data class Device(
    val id: String,
    val address: String,
    val name: String?,
    val lastSeenAt: Long,
) {
    val fallbackName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "CLumo-${id.take(4).uppercase()}"
}
