package io.github.cespresso.clumo.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.updateAll
import io.github.cespresso.clumo.MainActivity
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.appContainer
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.ble.ButtonEvent
import io.github.cespresso.clumo.data.session.DeviceSession
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
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
 * Per-device logic lives in [DeviceSession].
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
    private val registry: DeviceSessionRegistry get() = appContainer.registry
    private val patterns: PatternRepository get() = appContainer.patterns
    private val preferences: AppPreferences get() = appContainer.preferences

    private lateinit var widgetStore: WidgetSnapshotStore
    private lateinit var publisher: WidgetStatePublisher

    private var pendingCommand: WidgetCommand? = null
    private var lastWidgetFailureRealtime: Long? = null

    private var currentStatus: String = ""
    private var capturingAudio: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        currentStatus = getString(R.string.notif_idle)
        postForeground()
        observeConnections()
        observeVisualizerPreferences()
        observeAudioCapture()
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
    private suspend fun currentTarget(): Device? = resolvePrimaryTarget(preferences.primaryDeviceId.first(), repository.devices.value)

    /**
     * Connects to the primary device on behalf of a caller that has no address to hand in.
     * [DeviceSession.connect] guards itself and returns immediately unless the link is
     * down, so a live connection comes back untouched.
     */
    private fun ensureConnected(target: Device): DeviceSession = registry.connect(target.address, target.name)

    private suspend fun handleWidgetCommand(command: WidgetCommand) {
        val target = currentTarget()
        val existing = target?.let { registry.get(it.address) }
        val state = existing?.state?.value?.link ?: ConnectionState.Disconnected
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
    private suspend fun awaitReadyThenRun(connection: DeviceSession) {
        val startedAt = SystemClock.elapsedRealtime()
        val ready = withTimeoutOrNull(WidgetCommandGate.CONNECT_TIMEOUT_MS) {
            connection.state.map { it.link }.first { it == ConnectionState.Ready }
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

    private fun execute(command: WidgetCommand, connection: DeviceSession) {
        val observed = connection.state.value.observed
        when (command) {
            WidgetCommand.TogglePomodoro ->
                if (observed?.pomodoro?.isRunning == true) {
                    connection.pomodoroPause()
                } else {
                    connection.pomodoroStart()
                }
            WidgetCommand.ResetPomodoro -> connection.pomodoroReset()
            WidgetCommand.ToggleTimer ->
                if (observed?.timer?.isRunning == true) {
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
            .setContentIntent(deviceListIntent())
            .setOngoing(true)
            .build()

    /**
     * Tapping the notification lands on the device list, the same place the launcher opens.
     * CLEAR_TOP without SINGLE_TOP is the point: [MainActivity] keeps its back stack in memory,
     * so only a fresh instance is guaranteed to be showing the list rather than whatever screen
     * the app was left on.
     */
    private fun deviceListIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun updateNotification(status: String) {
        currentStatus = status
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    /**
     * Claims the foreground service with the types matching what the hub is doing now.
     *
     * A refused claim must not take the hub down with it: the BLE link keeps running on the
     * types already held, and the visualizer is the only thing that degrades.
     */
    private fun postForeground() {
        val notification = buildNotification(currentStatus)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceTypes(capturingAudio, hasRecordAudioPermission()),
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { failure -> Log.w(TAG, "Foreground service type refused", failure) }
    }

    private fun hasRecordAudioPermission(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Keeps the foreground notification text in sync with connection states. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeConnections() {
        registry.states
            .onEach { states ->
                val ready = states.values.count { it.link == ConnectionState.Ready }
                val status = if (ready > 0) {
                    getString(R.string.notif_connected)
                } else {
                    getString(R.string.notif_idle)
                }
                updateNotification(status)
            }
            .launchIn(scope)
    }

    /**
     * Holds the microphone service type for as long as a session is capturing.
     *
     * [android.media.audiofx.Visualizer] draws on RECORD_AUDIO, so a hub without that type is
     * handed silence the moment the app leaves the foreground. Capture only ever starts from
     * the device screen, which is why the claim is always made while the app still counts as
     * foreground and the platform will grant it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAudioCapture() {
        registry.sessions
            .flatMapLatest { map ->
                if (map.isEmpty()) {
                    flowOf(false)
                } else {
                    combine(map.values.map { it.visualizerActive }) { active -> active.any { it } }
                }
            }
            .onEach { capturing ->
                // Also swallows the first emission, which repeats what onCreate already claimed.
                if (capturing == capturingAudio) return@onEach
                capturingAudio = capturing
                postForeground()
            }
            .launchIn(scope)
    }

    private fun observeVisualizerPreferences() {
        combine(
            registry.sessions,
            preferences.visualizerSensitivity,
            preferences.automaticLowVolumeBoost,
        ) { connections, sensitivity, automaticBoost ->
            Triple(connections.values, sensitivity, automaticBoost)
        }.onEach { (sessions, sensitivity, automaticBoost) ->
            sessions.forEach {
                it.setVisualizerSensitivity(sensitivity)
                it.setAutomaticLowVolumeBoost(automaticBoost)
            }
        }.launchIn(scope)
    }

    /** Applies device button presses the firmware forwarded. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeButtonEvents() {
        registry.sessions
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
    private suspend fun applyButtonEvent(connection: DeviceSession, event: ButtonEvent) {
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

    private suspend fun cycleDisplayPattern(connection: DeviceSession, forward: Boolean) {
        val deviceId = connection.deviceId.value
            ?: repository.getByAddress(connection.address)?.id
            ?: connection.address
        val next = patterns.cycleApplied(deviceId, forward) ?: return
        connection.commitPattern(next)
    }

    private suspend fun stepVisualizerSensitivity(up: Boolean) {
        val current = preferences.visualizerSensitivity.first()
        preferences.setVisualizerSensitivity(steppedVisualizerSensitivity(current, up))
    }
}
