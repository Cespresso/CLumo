package io.github.cespresso.clumo

import android.content.Context
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRegistry
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.BleScanner

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

    val repository: DeviceRepository by lazy { DeviceRepository(appContext) }
    val registry: DeviceRegistry by lazy { DeviceRegistry(appContext, repository) }
    val scanner: BleScanner by lazy { BleScanner(appContext) }
    val patterns: PatternRepository by lazy { PatternRepository(appContext) }
    val preferences: AppPreferences by lazy { AppPreferences(appContext) }
}
