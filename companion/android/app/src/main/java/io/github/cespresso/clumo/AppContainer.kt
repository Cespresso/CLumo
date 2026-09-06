package io.github.cespresso.clumo

import android.content.Context
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.BleScanner
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.widget.WidgetSnapshotStore
import io.github.cespresso.clumo.widget.WidgetStatePublisher
import io.github.cespresso.clumo.widget.updateClumoWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's object graph.
 *
 * Every member outlives any one screen and any one service start, so the graph hangs off the
 * application. It used to hang off the hub service, which made binding to a foreground service
 * the price of reading a preference and let every screen reach the whole data layer through it.
 *
 * Built lazily: [DeviceRepository] and [PatternRepository] read storage in their constructors,
 * and a start builds only what it reaches. The hub started by a widget tap never builds the
 * scanner, and a process woken only to draw a widget builds none of this.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Work that belongs to the graph itself rather than to any screen or service start. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: DeviceRepository by lazy { DeviceRepository(appContext) }
    val registry: DeviceSessionRegistry by lazy {
        DeviceSessionRegistry(appContext, repository)
    }
    val scanner: BleScanner by lazy { BleScanner(appContext) }
    val preferences: AppPreferences by lazy { AppPreferences(appContext) }
    val patterns: PatternRepository by lazy {
        PatternRepository(appContext, preferences, repository).also { patterns ->
            // Seeding used to ride on the hub service's onCreate. The defaults belong to the
            // graph, not to whether a service happens to be running.
            scope.launch { patterns.ensureSeeded() }
        }
    }

    /**
     * Starts publishing on first access and never stops. The activity touches it on launch and
     * the hub service on start, which are the two ways anything a widget shows can change; a
     * process woken only to draw a widget reads the store and never builds this.
     */
    val widgetPublisher: WidgetStatePublisher by lazy {
        WidgetStatePublisher(
            context = appContext,
            registry = registry,
            repository = repository,
            preferences = preferences,
            patterns = patterns,
            store = WidgetSnapshotStore(appContext),
            onPublished = { updateClumoWidgets(appContext) },
        ).also { it.start(scope) }
    }
}
