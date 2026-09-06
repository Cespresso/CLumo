package io.github.cespresso.clumo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.Pattern
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.patternStore by preferencesDataStore(name = "clumo_patterns")

internal fun upsertPattern(patterns: List<Pattern>, pattern: Pattern): List<Pattern> {
    val index = patterns.indexOfFirst { it.id == pattern.id }
    return if (index >= 0) {
        patterns.toMutableList().apply { set(index, pattern) }
    } else {
        patterns + pattern
    }
}

internal data class PatternApplicationUpdate(
    val patterns: List<Pattern>,
    val appliedPatternIds: Map<String, String>,
)

internal fun saveAndApplyPattern(
    patterns: List<Pattern>,
    appliedPatternIds: Map<String, String>,
    deviceId: String,
    pattern: Pattern,
): PatternApplicationUpdate = PatternApplicationUpdate(
    patterns = upsertPattern(patterns, pattern),
    appliedPatternIds = appliedPatternIds + (deviceId to pattern.id),
)

internal fun appliedPatternIdsAfterRemoval(
    appliedPatternIds: Map<String, String>,
    removedPatternId: String,
): Map<String, String> = appliedPatternIds.filterValues { it != removedPatternId }

internal fun migrateLegacyPatternSelection(
    legacyPatternId: String?,
    appliedPatternIds: Map<String, String>,
    primaryDeviceId: String?,
    knownDeviceIds: List<String>,
): Map<String, String> {
    val patternId = legacyPatternId ?: return appliedPatternIds
    val target = primaryDeviceId ?: knownDeviceIds.firstOrNull() ?: return appliedPatternIds
    if (target in appliedPatternIds) return appliedPatternIds
    return appliedPatternIds + (target to patternId)
}

internal fun legacySelectionCanBeConsumed(
    primaryDeviceId: String?,
    knownDeviceIds: List<String>,
): Boolean = primaryDeviceId != null || knownDeviceIds.isNotEmpty()

internal fun encodeAppliedPatternIds(appliedPatternIds: Map<String, String>): String =
    JSONObject().apply {
        appliedPatternIds.forEach { (deviceId, patternId) -> put(deviceId, patternId) }
    }.toString()

internal fun decodeAppliedPatternIds(raw: String?): Map<String, String> {
    if (raw.isNullOrEmpty()) return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { deviceId -> put(deviceId, json.getString(deviceId)) }
        }
    }.getOrElse { emptyMap() }
}

/**
 * The pattern one step after [currentId] in list order, wrapping at both ends.
 * Returns the first pattern when [currentId] is missing from [patterns], and null
 * when there is nothing to select.
 */
internal fun cyclePattern(
    patterns: List<Pattern>,
    currentId: String?,
    forward: Boolean,
): Pattern? {
    if (patterns.isEmpty()) return null
    val currentIndex = patterns.indexOfFirst { it.id == currentId }
    if (currentIndex < 0) return patterns.first()
    val step = if (forward) 1 else -1
    return patterns[(currentIndex + step + patterns.size) % patterns.size]
}

/**
 * DataStore-backed store of user display patterns (JSON list of
 * {id, name, bits}) plus the applied pattern id for each device.
 * Seeds three default patterns on first run.
 */
class PatternRepository(
    private val context: Context,
    private val preferences: AppPreferences,
    private val devices: DeviceRepository,
) {

    companion object {
        private val KEY_PATTERNS = stringPreferencesKey("patterns_json")
        private val KEY_SELECTED = stringPreferencesKey("selected_pattern_id")
        private val KEY_APPLIED = stringPreferencesKey("applied_pattern_ids_json")
        private val KEY_SEEDED = booleanPreferencesKey("patterns_seeded")

        private val HEART_BITS = listOf(
            "00000000", "01100110", "11111111", "11111111",
            "01111110", "00111100", "00011000", "00000000",
        ).joinToString("")

        private val SMILE_BITS = listOf(
            "00000000", "01100110", "01100110", "00000000",
            "00000000", "10000001", "01000010", "00111100",
        ).joinToString("")

        private val STAR_BITS = listOf(
            "00011000", "00011000", "01111110", "00111100",
            "00011000", "00111100", "01100110", "00000000",
        ).joinToString("")
    }

    private val store = context.applicationContext.patternStore

    val patterns: Flow<List<Pattern>> = store.data.map { prefs ->
        decode(prefs[KEY_PATTERNS])
    }

    val appliedPatternIds: Flow<Map<String, String>> = store.data.map {
        decodeAppliedPatternIds(it[KEY_APPLIED])
    }

    /** Seed the default patterns exactly once. */
    suspend fun ensureSeeded() {
        migrateLegacySelection()
        val seeded = store.data.first()[KEY_SEEDED] ?: false
        if (seeded) return
        val defaults = listOf(
            Pattern(UUID.randomUUID().toString(), context.getString(R.string.pattern_default_heart), HEART_BITS),
            Pattern(UUID.randomUUID().toString(), context.getString(R.string.pattern_default_smile), SMILE_BITS),
            Pattern(UUID.randomUUID().toString(), context.getString(R.string.pattern_default_star), STAR_BITS),
        )
        store.edit { prefs ->
            if (prefs[KEY_SEEDED] == true) return@edit
            prefs[KEY_PATTERNS] = encode(defaults)
            prefs[KEY_SEEDED] = true
        }
    }

    private suspend fun migrateLegacySelection() {
        val primaryId = preferences.primaryDeviceId.first()
        val knownIds = devices.devices.value.map { it.id }
        store.edit { prefs ->
            val legacy = prefs[KEY_SELECTED] ?: return@edit
            if (!legacySelectionCanBeConsumed(primaryId, knownIds)) return@edit
            val migrated = migrateLegacyPatternSelection(
                legacyPatternId = legacy,
                appliedPatternIds = decodeAppliedPatternIds(prefs[KEY_APPLIED]),
                primaryDeviceId = primaryId,
                knownDeviceIds = knownIds,
            )
            if (migrated.isNotEmpty()) {
                prefs[KEY_APPLIED] = encodeAppliedPatternIds(migrated)
            }
            prefs.remove(KEY_SELECTED)
        }
    }

    suspend fun upsert(pattern: Pattern) {
        store.edit { prefs ->
            val current = decode(prefs[KEY_PATTERNS])
            prefs[KEY_PATTERNS] = encode(upsertPattern(current, pattern))
        }
    }

    suspend fun saveAndApply(deviceId: String, pattern: Pattern) {
        store.edit { prefs ->
            val update = saveAndApplyPattern(
                patterns = decode(prefs[KEY_PATTERNS]),
                appliedPatternIds = decodeAppliedPatternIds(prefs[KEY_APPLIED]),
                deviceId = deviceId,
                pattern = pattern,
            )
            prefs[KEY_PATTERNS] = encode(update.patterns)
            prefs[KEY_APPLIED] = encodeAppliedPatternIds(update.appliedPatternIds)
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val updated = decode(prefs[KEY_PATTERNS]).filter { it.id != id }
            prefs[KEY_PATTERNS] = encode(updated)
            val applied = appliedPatternIdsAfterRemoval(
                decodeAppliedPatternIds(prefs[KEY_APPLIED]),
                id,
            )
            if (applied.isEmpty()) {
                prefs.remove(KEY_APPLIED)
            } else {
                prefs[KEY_APPLIED] = encodeAppliedPatternIds(applied)
            }
        }
    }

    suspend fun setApplied(deviceId: String, patternId: String) {
        store.edit { prefs ->
            val applied = decodeAppliedPatternIds(prefs[KEY_APPLIED]) + (deviceId to patternId)
            prefs[KEY_APPLIED] = encodeAppliedPatternIds(applied)
        }
    }

    suspend fun cycleApplied(deviceId: String, forward: Boolean): Pattern? {
        var selected: Pattern? = null
        store.edit { prefs ->
            val current = decode(prefs[KEY_PATTERNS])
            val applied = decodeAppliedPatternIds(prefs[KEY_APPLIED])
            val currentId = applied[deviceId]
            val next = cyclePattern(current, currentId, forward) ?: return@edit
            if (next.id == currentId) return@edit
            selected = next
            prefs[KEY_APPLIED] = encodeAppliedPatternIds(applied + (deviceId to next.id))
        }
        return selected
    }

    private fun decode(raw: String?): List<Pattern> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Pattern(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    bits = obj.getString("bits"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun encode(patterns: List<Pattern>): String {
        val array = JSONArray()
        patterns.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("bits", p.bits)
                }
            )
        }
        return array.toString()
    }
}
