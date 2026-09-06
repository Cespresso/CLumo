package io.github.cespresso.clumo.data

import android.content.Context
import android.content.SharedPreferences
import io.github.cespresso.clumo.domain.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for known CLumo devices.
 * Backed by SharedPreferences with JSON-encoded entries keyed by device UUID.
 */
class DeviceRepository(context: Context) {

    companion object {
        private const val PREFS = "clumo_devices"
        private const val KEY_DEVICES = "devices_json"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<Device>>(load())
    val devices = _devices.asStateFlow()

    fun upsert(device: Device) {
        val current = _devices.value.filter { it.id != device.id }
        val updated = (current + device).sortedByDescending { it.lastSeenAt }
        _devices.value = updated
        persist(updated)
    }

    fun remove(deviceId: String) {
        val updated = _devices.value.filter { it.id != deviceId }
        _devices.value = updated
        persist(updated)
    }

    fun get(deviceId: String): Device? = _devices.value.firstOrNull { it.id == deviceId }

    fun getByAddress(address: String): Device? = _devices.value.firstOrNull { it.address == address }

    private fun load(): List<Device> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Device(
                    id = obj.getString("id"),
                    address = obj.getString("address"),
                    name = obj.optString("name").takeIf { it.isNotEmpty() },
                    lastSeenAt = obj.optLong("lastSeenAt"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun persist(devices: List<Device>) {
        val array = JSONArray()
        devices.forEach { d ->
            array.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("address", d.address)
                    put("name", d.name ?: "")
                    put("lastSeenAt", d.lastSeenAt)
                }
            )
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }
}
