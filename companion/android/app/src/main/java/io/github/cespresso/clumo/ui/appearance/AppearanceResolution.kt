package io.github.cespresso.clumo.ui.appearance

import io.github.cespresso.clumo.domain.DeviceAppearance

fun resolveAppearance(
    deviceId: String?,
    appearances: Map<String, DeviceAppearance>,
): DeviceAppearance = deviceId?.let(appearances::get) ?: DeviceAppearance.DEFAULT
