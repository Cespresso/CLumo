package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.DeviceNaming
import org.json.JSONArray
import org.json.JSONObject

internal fun decodeDevices(raw: String): List<Device> =
    runCatching {
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val kind = DeviceKind.entries.firstOrNull { it.name == obj.optString("kind") } ?: DeviceKind.CLUMO
            Device(
                id = obj.getString("id"),
                address = obj.getString("address"),
                name = if (kind == DeviceKind.HEADPHONE_STAND) obj.optString("name").takeIf { it.isNotBlank() } else DeviceNaming.ownName(obj.optString("name")),
                lastSeenAt = obj.optLong("lastSeenAt"),
                kind = kind,
            )
        }
    }.getOrElse { emptyList() }

internal fun encodeDevices(devices: List<Device>): String =
    JSONArray().apply {
        devices.forEach { device ->
            put(
                JSONObject().apply {
                    put("id", device.id)
                    put("address", device.address)
                    put("name", device.name ?: "")
                    put("lastSeenAt", device.lastSeenAt)
                    put("kind", device.kind.name)
                },
            )
        }
    }.toString()
