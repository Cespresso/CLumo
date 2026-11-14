package io.github.cespresso.clumo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.mergeKnownDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val updated = mergeKnownDevice(_devices.value, device)
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
        return decodeDevices(raw)
    }

    private fun persist(devices: List<Device>) {
        prefs.edit { putString(KEY_DEVICES, encodeDevices(devices)) }
    }
}
