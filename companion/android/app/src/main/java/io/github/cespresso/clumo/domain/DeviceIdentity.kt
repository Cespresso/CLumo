package io.github.cespresso.clumo.domain

/**
 * One physical CLumo per record. A device is the same hardware when either its firmware
 * id or its MAC matches: the id survives a MAC change, and the MAC survives a reset that
 * regenerates the id, so a record matching on either is replaced rather than duplicated.
 */
fun mergeKnownDevice(current: List<Device>, device: Device): List<Device> =
    (current.filter { it.id != device.id && it.address != device.address } + device)
        .sortedByDescending { it.lastSeenAt }

/**
 * Re-keys a per-device setting from a superseded id to the id that replaced it. A value the
 * new id already has wins, since it was set after the reset and is what the user sees now.
 */
fun <V> moveDeviceKey(settings: Map<String, V>, fromId: String, toId: String): Map<String, V> {
    if (fromId == toId) return settings
    val moved = settings[fromId] ?: return settings
    val result = settings.toMutableMap()
    result.remove(fromId)
    result.putIfAbsent(toId, moved)
    return result
}
