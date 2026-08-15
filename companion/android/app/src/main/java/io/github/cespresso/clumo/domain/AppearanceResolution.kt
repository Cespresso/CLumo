package io.github.cespresso.clumo.domain

/**
 * The look of a device, or the canonical default when it has none stored. A device is only
 * known by its id once the link reports one, so every caller has to survive a null.
 */
fun resolveAppearance(
    deviceId: String?,
    appearances: Map<String, DeviceAppearance>,
): DeviceAppearance = deviceId?.let(appearances::get) ?: DeviceAppearance.DEFAULT
