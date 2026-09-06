package io.github.cespresso.clumo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.updateAll
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.appContainer
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRegistry
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.ButtonEvent
import io.github.cespresso.clumo.data.ble.DeviceConnection
import io.github.cespresso.clumo.data.steppedVisualizerSensitivity
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.resolvePrimaryTarget
import io.github.cespresso.clumo.widget.ClumoControlWidget
import io.github.cespresso.clumo.widget.ClumoPresenceWidget
import io.github.cespresso.clumo.widget.GateDecision
import io.github.cespresso.clumo.widget.WidgetCommand
import io.github.cespresso.clumo.widget.WidgetCommandGate
import io.github.cespresso.clumo.widget.WidgetSnapshotStore
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that keeps the device hub alive, so BLE connections and visualizer
 * streaming survive while the app is backgrounded. It holds a lifecycle, not the graph: the
 * registry and repositories it drives belong to [io.github.cespresso.clumo.AppContainer].
 * Per-device logic lives in [io.github.cespresso.clumo.data.ble.DeviceConnection].
 */
class DeviceHubService : Service() {

    companion object {
        private const val TAG = "DeviceHubService"
        private const val CHANNEL_ID = "clumo_hub"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, command: WidgetCommand? = null) {
            val intent = Intent(context, DeviceHubService::class.java)
            if (command != null) {
                intent.action = WidgetCommand.ACTION
                intent.putExtra(WidgetCommand.EXTRA, WidgetCommand.encode(command))
            }
            context.startForegroundService(intent)
        }
    }

    /** Nothing binds to the hub; it is started, not consulted. */
    override fun onBind(intent: Intent?): IBinder? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val repository: DeviceRepository get() = appContainer.repository
    private val registry: DeviceRegistry get() = appContainer.registry
    private val patterns: PatternRepository get() = appContainer.patterns
    private val preferences: AppPreferences get() = appContainer.preferences

    private lateinit var widgetStore: WidgetSnapshotStore
    private lateinit var publisher: WidgetStatePublisher

    private var pendingCommand: WidgetCommand? = null
    private var lastWidgetFailureRealtime: Long? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_idle)))
        observeConnections()
        observeVisualizerPreferences()
        observeButtonEvents()
        scope.launch { patterns.ensureSeeded() }
        widgetStore = WidgetSnapshotStore(this)
        publisher = WidgetStatePublisher(
            context = this,
            registry = registry,
            repository = repository,
            preferences = preferences,
            patterns = patterns,
            store = widgetStore,
            onPublished = { updateAllWidgets(this) },
        )
        publisher.start(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == WidgetCommand.ACTION) {
            WidgetCommand.decode(intent.getStringExtra(WidgetCommand.EXTRA))?.let {
                scope.launch { handleWidgetCommand(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        registry.disconnectAll()
        scope.cancel()
        super.onDestroy()
    }

    // --- Widgets ---

    /**
     * The device a widget command acts on. Read from the preference rather than a cache:
     * a widget tap can start this process, and a collector filled asynchronously in
     * [onCreate] has not necessarily emitted by the time the command arrives.
     */
    private suspend fun currentTarget(): Device? =
        resolvePrimaryTarget(preferences.primaryDeviceId.first(), repository.devices.value)

    /**
     * Connects to the primary device on behalf of a caller that has no address to hand in.
     * [DeviceConnection.connect] guards itself and returns immediately unless the link is
     * down, so a live connection comes back untouched.
     */
    private fun ensureConnected(target: Device): DeviceConnection =
        registry.connect(target.address, target.name)

    private suspend fun handleWidgetCommand(command: WidgetCommand) {
        val target = currentTarget()
        val existing = target?.let { registry.get(it.address) }
        val state = existing?.connectionState?.value ?: ConnectionState.Disconnected
        when (
            WidgetCommandGate.decide(
                command = command,
                connectionState = state,
                lastFailureRealtime = lastWidgetFailureRealtime,
                nowRealtime = SystemClock.elapsedRealtime(),
            )
        ) {
            GateDecision.Execute -> {
                lastWidgetFailureRealtime = null
                publisher.setCommandFailed(false)
                execute(command, requireNotNull(existing))
            }
            GateDecision.Refuse -> Unit
            GateDecision.ConnectThenExecute -> {
                if (target == null) {
                    // Say so on the widget; a silently dropped tap looks broken.
                    publisher.publishNoTarget()
                    return
                }
                publisher.setCommandFailed(false)
                val connection = ensureConnected(target)
                // A second command replaces the first; holding one is enough.
                pendingCommand = command
                awaitReadyThenRun(connection)
            }
        }
    }

    /**
     * The elapsed-time check is not redundant with the timeout: [withTimeoutOrNull] measures
     * dispatcher time, so a wait suspended by doze can return non-null well past 20s of wall
     * clock, and a command that late must not fire.
     */
    private suspend fun awaitReadyThenRun(connection: DeviceConnection) {
        val startedAt = SystemClock.elapsedRealtime()
        val ready = withTimeoutOrNull(WidgetCommandGate.CONNECT_TIMEOUT_MS) {
            connection.connectionState.first { it == ConnectionState.Ready }
        }
        if (ready == null || WidgetCommandGate.hasTimedOut(startedAt, SystemClock.elapsedRealtime())) {
            pendingCommand = null
            lastWidgetFailureRealtime = SystemClock.elapsedRealtime()
            publisher.setCommandFailed(true)
            return
        }
        lastWidgetFailureRealtime = null
        // An earlier waiter may have timed out and flagged a failure while this one was
        // still going. Reaching Ready settles it.
        publisher.setCommandFailed(false)
        pendingCommand?.let { execute(it, connection) }
        pendingCommand = null
    }

    private fun execute(command: WidgetCommand, connection: DeviceConnection) {
        when (command) {
            WidgetCommand.TogglePomodoro ->
                if (connection.pomodoroStatus.value?.isRunning == true) {
                    connection.pomodoroPause()
                } else {
                    connection.pomodoroStart()
                }
            WidgetCommand.ResetPomodoro -> connection.pomodoroReset()
            WidgetCommand.ToggleTimer ->
                if (connection.timerStatus.value?.isRunning == true) {
                    connection.timerPause()
                } else {
                    connection.timerStart()
                }
            WidgetCommand.CancelTimer -> connection.timerCancel()
            WidgetCommand.Retry -> Unit // reaching Ready was the whole point
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        ClumoControlWidget().updateAll(context)
        ClumoPresenceWidget().updateAll(context)
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

    /** Applies device button presses the firmware forwarded. */
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

    /**
     * A failed preference write must cost one press, not the whole collector: an
     * exception escaping here would cancel the observer for the service's lifetime.
     */
    private suspend fun applyButtonEvent(connection: DeviceConnection, event: ButtonEvent) {
        try {
            when (event.mode) {
                DeviceMode.DISPLAY -> cycleDisplayPattern(connection, event.isMain)
                DeviceMode.VISUALIZER -> stepVisualizerSensitivity(event.isMain)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Button action failed", e)
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
