package io.github.cespresso.clumo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.widgetStore by preferencesDataStore(name = "clumo_widget")

/** The single snapshot both widgets read. Written by the publisher, never by a widget. */
class WidgetSnapshotStore(context: Context) {

    private val store = context.applicationContext.widgetStore

    private companion object {
        const val TAG = "WidgetSnapshotStore"
        val KEY_SNAPSHOT = stringPreferencesKey("snapshot_json")
    }

    val snapshots: Flow<WidgetSnapshot?> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decodeWidgetSnapshot(it[KEY_SNAPSHOT]) }

    suspend fun read(): WidgetSnapshot? = snapshots.first()

    /**
     * Returns false when the snapshot could not be persisted. An exception escaping here
     * would unwind through the publisher and cancel its collector for the process's lifetime,
     * so a storage failure costs one update rather than every future one.
     */
    suspend fun write(snapshot: WidgetSnapshot): Boolean =
        try {
            store.edit { it[KEY_SNAPSHOT] = encodeWidgetSnapshot(snapshot) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not persist the widget snapshot", e)
            false
        }
}
