package io.github.cespresso.clumo.widget

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

/** The snapshot as a widget may draw it, or null once it is too old to be believed. */
internal fun WidgetSnapshot?.freshOrNull(nowRealtime: Long): WidgetSnapshot? =
    this?.takeUnless { it.isStale(nowRealtime) }

/**
 * What a widget is allowed to react to. Timestamp-only changes are dropped, so the service's
 * heartbeat costs no update, but they are dropped after the staleness check, so a snapshot
 * aging out still moves the widget.
 */
internal fun Flow<WidgetSnapshot?>.freshContentOnly(
    nowRealtime: () -> Long,
): Flow<WidgetSnapshot?> = map { it.freshOrNull(nowRealtime()) }
    .distinctUntilChangedBy { it?.copy(updatedAtRealtime = 0L) }

/**
 * The one way a widget reads the store. Collecting inside the composition, rather than
 * capturing a value before it, is what keeps a Glance session live. [seed] is the value read
 * before the session opened, so the first frame draws the device instead of flashing the
 * disconnected fallback while DataStore catches up.
 */
@Composable
internal fun rememberWidgetSnapshot(
    store: WidgetSnapshotStore,
    seed: WidgetSnapshot?,
): WidgetSnapshot? {
    val fresh = remember(store) { store.snapshots.freshContentOnly(SystemClock::elapsedRealtime) }
    val snapshot by fresh.collectAsState(initial = seed.freshOrNull(SystemClock.elapsedRealtime()))
    return snapshot
}
