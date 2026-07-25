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

internal data class PatternSelectionUpdate(
    val patterns: List<Pattern>,
    val selectedId: String,
)

internal fun saveAndSelectPattern(
    patterns: List<Pattern>,
    pattern: Pattern,
): PatternSelectionUpdate = PatternSelectionUpdate(
    patterns = upsertPattern(patterns, pattern),
    selectedId = pattern.id,
)

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
 * {id, name, bits}) plus the currently selected pattern id.
 * Seeds three default patterns on first run.
 */
class PatternRepository(private val context: Context) {

    companion object {
        private val KEY_PATTERNS = stringPreferencesKey("patterns_json")
        private val KEY_SELECTED = stringPreferencesKey("selected_pattern_id")
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

    val selectedId: Flow<String?> = store.data.map { it[KEY_SELECTED] }

    /** Seed the default patterns exactly once. */
    suspend fun ensureSeeded() {
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
            prefs[KEY_SELECTED] = defaults.first().id
            prefs[KEY_SEEDED] = true
        }
    }

    suspend fun upsert(pattern: Pattern) {
        store.edit { prefs ->
            val current = decode(prefs[KEY_PATTERNS])
            prefs[KEY_PATTERNS] = encode(upsertPattern(current, pattern))
        }
    }

    suspend fun saveAndSelect(pattern: Pattern) {
        store.edit { prefs ->
            val current = decode(prefs[KEY_PATTERNS])
            val update = saveAndSelectPattern(current, pattern)
            prefs[KEY_PATTERNS] = encode(update.patterns)
            prefs[KEY_SELECTED] = update.selectedId
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val updated = decode(prefs[KEY_PATTERNS]).filter { it.id != id }
            prefs[KEY_PATTERNS] = encode(updated)
            if (prefs[KEY_SELECTED] == id) {
                val fallback = updated.firstOrNull()?.id
                if (fallback != null) prefs[KEY_SELECTED] = fallback else prefs.remove(KEY_SELECTED)
            }
        }
    }

    suspend fun select(id: String) {
        store.edit { it[KEY_SELECTED] = id }
    }

    /**
     * Move the selection one step and return the newly selected pattern. Returns null
     * when the selection could not move because there are fewer than two patterns.
     */
    suspend fun cycleSelection(forward: Boolean): Pattern? {
        var selected: Pattern? = null
        store.edit { prefs ->
            val current = decode(prefs[KEY_PATTERNS])
            val currentId = prefs[KEY_SELECTED]
            val next = cyclePattern(current, currentId, forward) ?: return@edit
            if (next.id == currentId) return@edit
            selected = next
            prefs[KEY_SELECTED] = next.id
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
