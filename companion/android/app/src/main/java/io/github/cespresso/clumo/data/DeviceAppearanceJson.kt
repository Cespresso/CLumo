package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import org.json.JSONObject

internal fun decodeDeviceAppearances(raw: String?): Map<String, DeviceAppearance> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { deviceId ->
                val value = root.optJSONObject(deviceId) ?: return@forEach
                val defaults = DeviceAppearance.DEFAULT
                put(
                    deviceId,
                    DeviceAppearance(
                        enclosureColor = value.rgbOrDefault("enclosure", defaults.enclosureColor),
                        buttonAColor = value.rgbOrDefault("buttonA", defaults.buttonAColor),
                        buttonBColor = value.rgbOrDefault("buttonB", defaults.buttonBColor),
                        ledColor = value.rgbOrDefault("led", defaults.ledColor),
                    ),
                )
            }
        }
    }.getOrElse { emptyMap() }
}

internal fun encodeDeviceAppearances(appearances: Map<String, DeviceAppearance>): String {
    val root = JSONObject()
    appearances.forEach { (deviceId, appearance) ->
        root.put(
            deviceId,
            JSONObject().apply {
                put("enclosure", appearance.enclosureColor.toHex())
                put("buttonA", appearance.buttonAColor.toHex())
                put("buttonB", appearance.buttonBColor.toHex())
                put("led", appearance.ledColor.toHex())
            },
        )
    }
    return root.toString()
}

internal fun updatedDeviceAppearances(
    current: Map<String, DeviceAppearance>,
    deviceId: String,
    appearance: DeviceAppearance?,
): Map<String, DeviceAppearance> =
    current.toMutableMap().apply {
        if (appearance == null) remove(deviceId) else put(deviceId, appearance)
    }

private fun JSONObject.rgbOrDefault(key: String, default: RgbColor): RgbColor = optString(key).takeIf { it.isNotEmpty() }?.let(RgbColor::parseOrNull) ?: default
