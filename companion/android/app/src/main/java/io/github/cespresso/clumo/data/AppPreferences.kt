package io.github.cespresso.clumo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "clumo_prefs")

internal fun sanitizeVisualizerSensitivity(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.6f

internal fun interpretStoredVisualizerSensitivity(value: Float?): Float =
    sanitizeVisualizerSensitivity(value ?: 0.6f)

internal const val VISUALIZER_SENSITIVITY_STEP = 0.1f

/**
 * [current] moved one step, clamped to `0..1`. The result is rounded to the step
 * granularity so repeated stepping does not accumulate binary floating-point error.
 */
internal fun steppedVisualizerSensitivity(current: Float, up: Boolean): Float {
    val base = sanitizeVisualizerSensitivity(current)
    val delta = if (up) VISUALIZER_SENSITIVITY_STEP else -VISUALIZER_SENSITIVITY_STEP
    val factor = 1f / VISUALIZER_SENSITIVITY_STEP
    val stepped = ((base + delta) * factor).roundToInt() / factor
    return sanitizeVisualizerSensitivity(stepped)
}

/**
 * App-level DataStore preferences: onboarding completion, visualizer settings,
 * and local device aliases keyed by the firmware device id.
 * Aliases are never written to the device.
 */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_ALIASES = stringPreferencesKey("device_aliases_json")
        private val KEY_VISUALIZER_SENSITIVITY = floatPreferencesKey("visualizer_sensitivity")
        private val KEY_VISUALIZER_AUTO_LOW_VOLUME_BOOST =
            booleanPreferencesKey("visualizer_auto_low_volume_boost")
    }

    private val data = store.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    val onboardingDone: Flow<Boolean> = data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        store.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    /** Map of device id -> user alias. */
    val aliases: Flow<Map<String, String>> = data.map { prefs ->
        decodeAliases(prefs[KEY_ALIASES])
    }

    val visualizerSensitivity: Flow<Float> =
        data.map { interpretStoredVisualizerSensitivity(it[KEY_VISUALIZER_SENSITIVITY]) }

    val automaticLowVolumeBoost: Flow<Boolean> =
        data.map { it[KEY_VISUALIZER_AUTO_LOW_VOLUME_BOOST] ?: false }

    suspend fun setAlias(deviceId: String, alias: String?) {
        store.edit { prefs ->
            val current = decodeAliases(prefs[KEY_ALIASES]).toMutableMap()
            if (alias.isNullOrBlank()) current.remove(deviceId) else current[deviceId] = alias.trim()
            prefs[KEY_ALIASES] = JSONObject(current as Map<*, *>).toString()
        }
    }

    suspend fun setVisualizerSensitivity(value: Float) {
        store.edit { it[KEY_VISUALIZER_SENSITIVITY] = sanitizeVisualizerSensitivity(value) }
    }

    suspend fun setAutomaticLowVolumeBoost(enabled: Boolean) {
        store.edit { it[KEY_VISUALIZER_AUTO_LOW_VOLUME_BOOST] = enabled }
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
