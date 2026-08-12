package io.github.cespresso.clumo.domain

/**
 * The device a widget should act on. [primaryDeviceId] may dangle, so an id matching no known
 * device counts as unset. A single known device is then the answer; several are ambiguous, and
 * null tells the widget to ask.
 */
fun resolvePrimaryTarget(primaryDeviceId: String?, knownDevices: List<Device>): Device? {
    val designated = primaryDeviceId?.let { id -> knownDevices.firstOrNull { it.id == id } }
    if (designated != null) return designated
    return knownDevices.singleOrNull()
}
