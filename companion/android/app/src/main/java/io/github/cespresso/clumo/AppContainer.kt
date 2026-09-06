package io.github.cespresso.clumo

import android.content.Context
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.BleScanner
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
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
 * and a cold start that only shows onboarding should not pay for devices it has never seen.
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
}
