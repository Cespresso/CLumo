package io.github.cespresso.clumo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRegistry
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.BleScanner
import io.github.cespresso.clumo.data.ble.BleUuids
import io.github.cespresso.clumo.data.ble.ButtonEvent
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.data.steppedVisualizerSensitivity
import io.github.cespresso.clumo.domain.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the device hub (registry, repositories, scanner)
 * so BLE connections and visualizer streaming survive while the app is backgrounded.
 * Per-device logic lives in [io.github.cespresso.clumo.data.ble.DeviceConnection].
 */
class DeviceHubService : Service() {

    companion object {
        private const val CHANNEL_ID = "clumo_hub"
        private const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        val service: DeviceHubService get() = this@DeviceHubService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var repository: DeviceRepository
        private set
    lateinit var registry: DeviceRegistry
        private set
    lateinit var scanner: BleScanner
        private set
    lateinit var patterns: PatternRepository
        private set
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(this)
        registry = DeviceRegistry(this, repository)
        scanner = BleScanner(this)
        patterns = PatternRepository(this)
        preferences = AppPreferences(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_idle)))
        observeConnections()
        observeVisualizerPreferences()
        observeButtonEvents()
        scope.launch { patterns.ensureSeeded() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        registry.disconnectAll()
        scope.cancel()
        super.onDestroy()
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    /** Keeps the foreground notification text in sync with connection states. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeConnections() {
        registry.connections
            .flatMapLatest { map ->
                if (map.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(map.values.map { it.connectionState }) { it.toList() }
                }
            }
            .onEach { states ->
                val ready = states.count { it == ConnectionState.Ready }
                val status = if (ready > 0) {
                    getString(R.string.notif_connected)
                } else {
                    getString(R.string.notif_idle)
                }
                updateNotification(status)
            }
            .launchIn(scope)
    }

    private fun observeVisualizerPreferences() {
        combine(
            registry.connections,
            preferences.visualizerSensitivity,
            preferences.automaticLowVolumeBoost,
        ) { connections, sensitivity, automaticBoost ->
            Triple(connections.values, sensitivity, automaticBoost)
        }.onEach { (connections, sensitivity, automaticBoost) ->
            connections.forEach {
                it.audioVisualizer.sensitivity = sensitivity
                it.audioVisualizer.automaticLowVolumeBoost = automaticBoost
            }
        }.launchIn(scope)
    }

    /**
     * Applies device button presses the firmware forwarded. Runs here rather than in
     * the UI so cycling and sensitivity keep working while the app is backgrounded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeButtonEvents() {
        registry.connections
            .flatMapLatest { map ->
                if (map.isEmpty()) {
                    emptyFlow()
                } else {
                    map.values.map { connection ->
                        connection.buttonEvents.map { connection to it }
                    }.merge()
                }
            }
            .onEach { (connection, event) -> applyButtonEvent(connection, event) }
            .launchIn(scope)
    }

    private suspend fun applyButtonEvent(connection: DeviceConnection, event: ButtonEvent) {
        when (event.mode) {
            BleUuids.MODE_DISPLAY -> cycleDisplayPattern(connection, event.isMain)
            BleUuids.MODE_VISUALIZER -> stepVisualizerSensitivity(event.isMain)
        }
    }

    private suspend fun cycleDisplayPattern(connection: DeviceConnection, forward: Boolean) {
        val next = patterns.cycleSelection(forward) ?: return
        connection.writeDisplay(next.toRowBytes())
    }

    private suspend fun stepVisualizerSensitivity(up: Boolean) {
        val current = preferences.visualizerSensitivity.first()
        preferences.setVisualizerSensitivity(steppedVisualizerSensitivity(current, up))
    }
}
