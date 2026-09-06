package io.github.cespresso.clumo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "clumo_prefs")

/**
 * App-level DataStore preferences: onboarding completion flag and
 * local device aliases keyed by the firmware device id.
 * Aliases are never written to the device.
 */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_ALIASES = stringPreferencesKey("device_aliases_json")
    }

    val onboardingDone: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        store.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    /** Map of device id -> user alias. */
    val aliases: Flow<Map<String, String>> = store.data.map { prefs ->
        decodeAliases(prefs[KEY_ALIASES])
    }

    suspend fun setAlias(deviceId: String, alias: String?) {
        store.edit { prefs ->
            val current = decodeAliases(prefs[KEY_ALIASES]).toMutableMap()
            if (alias.isNullOrBlank()) current.remove(deviceId) else current[deviceId] = alias.trim()
            prefs[KEY_ALIASES] = JSONObject(current as Map<*, *>).toString()
        }
    }

    private fun decodeAliases(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.getString(key)) }
            }
        }.getOrElse { emptyMap() }
    }
}
