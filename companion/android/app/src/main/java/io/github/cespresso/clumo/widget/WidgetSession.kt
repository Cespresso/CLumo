package io.github.cespresso.clumo.widget

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

/**
 * The snapshot as a widget may draw it: itself while fresh, its device with the link gone once
 * it is too old to be believed. Going stale costs the live state, not the identity.
 */
internal fun WidgetSnapshot.aged(nowRealtime: Long): WidgetSnapshot = if (isStale(nowRealtime)) asDisconnected() else this

/**
 * What a widget is allowed to react to. Timestamp-only changes are dropped, so the service's
 * heartbeat costs no update, but they are dropped after the snapshot is aged, so one aging
 * out still moves the widget.
 */
internal fun Flow<WidgetSnapshot?>.agedContentOnly(
    nowRealtime: () -> Long,
): Flow<WidgetSnapshot?> =
    map { it?.aged(nowRealtime()) }
        .distinctUntilChangedBy { it?.copy(updatedAtRealtime = 0L) }

/**
 * The one way a widget reads the store. Collecting inside the composition, rather than
 * capturing a value before it, is what keeps a Glance session live. [seed] is the value read
 * before the session opened, so the first frame draws the device instead of flashing the
 * disconnected fallback while DataStore catches up. Null only before anything was ever
 * published.
 */
@Composable
internal fun rememberWidgetSnapshot(
    store: WidgetSnapshotStore,
    seed: WidgetSnapshot?,
): WidgetSnapshot? {
    val aged = remember(store) { store.snapshots.agedContentOnly(SystemClock::elapsedRealtime) }
    val snapshot by aged.collectAsState(initial = seed?.aged(SystemClock.elapsedRealtime()))
    return snapshot
}
