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
import io.github.cespresso.clumo.domain.patternFor
import io.github.cespresso.clumo.domain.resolvePrimaryTarget
import io.github.cespresso.clumo.widget.GateDecision
import io.github.cespresso.clumo.widget.WidgetCommand
import io.github.cespresso.clumo.widget.WidgetCommandGate
import io.github.cespresso.clumo.widget.WidgetStatePublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that keeps the device hub alive, so BLE connections and visualizer
 * streaming survive while the app is backgrounded. It holds a lifecycle, not the graph: the
 * registry and repositories it drives belong to [io.github.cespresso.clumo.AppContainer].
 * Per-device logic lives in [DeviceSession].
 *
 * It runs while a session has work for it or a widget command is in flight, and stops itself
 * a grace period after both are gone. The container starts it for the sessions; a widget tap
 * starts it for the command.
 */
class DeviceHubService : Service() {

    companion object {
        private const val TAG = "DeviceHubService"
        private const val CHANNEL_ID = "clumo_hub"
        private const val NOTIFICATION_ID = 1

        private val _running = MutableStateFlow(false)

        /** Whether an instance is up. What the container's watcher reads to decide on a start. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

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
    private val publisher: WidgetStatePublisher get() = appContainer.widgetPublisher

    private var pendingCommand: WidgetCommand? = null
    private var lastWidgetFailureRealtime: Long? = null

    /**
     * Commands received and not yet finished. Kept apart from [pendingCommand], which is only
     * set once the command has resolved its target, after a suspension: a stop decided during
     * that suspension would cancel the command with nothing to say it was ever there.
     */
    private val inFlightCommands = MutableStateFlow(0)
    private var lastStartId = 0

    private var currentStatus: String = ""
    private var capturingAudio: Boolean = false

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        createNotificationChannel()
        currentStatus = getString(R.string.notif_idle)
        postForeground()
        observeConnections()
        observeVisualizerPreferences()
        observeAudioCapture()
        observeButtonEvents()
        stopWhenIdle()
        // A widget tap can start this process straight into the service, and the tap's
        // outcome has to be drawn: the publisher is what draws it.
        publisher
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == WidgetCommand.ACTION) {
            WidgetCommand.decode(intent.getStringExtra(WidgetCommand.EXTRA))?.let { command ->
                // Counted here, before anything suspends, so the idle check sees it.
                inFlightCommands.update { it + 1 }
                scope.launch {
                    try {
                        handleWidgetCommand(command)
                    } finally {
                        inFlightCommands.update { it - 1 }
                    }
                }
            }
        }
        // A restart after a kill would arrive with a null intent and no sessions to protect:
        // the links died with the process, and nothing here reconnects them.
        return START_NOT_STICKY
    }

    /** The sessions belong to the container and outlive this service; a screen may be holding one. */
    override fun onDestroy() {
        scope.cancel()
        _running.value = false
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
     * A refused claim for an extra type must not take the hub down with it: the claim is made
     * again with the base type alone, so the BLE link keeps running and the visualizer is the
     * only thing that degrades. If even the base type is refused there is no foreground service
     * to be, and a started service that never reaches the foreground is killed by the system a
     * few seconds later with a less useful trace, so it stops itself.
     */
    private fun postForeground() {
        val notification = buildNotification(currentStatus)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification)
            return
        }
        val wanted = foregroundServiceTypes(
            capturingAudio = capturingAudio,
            recordAudioGranted = holds(Manifest.permission.RECORD_AUDIO),
            bluetoothConnectGranted = holds(Manifest.permission.BLUETOOTH_CONNECT),
        )
        val base = foregroundServiceTypes(
            capturingAudio = false,
            recordAudioGranted = false,
            bluetoothConnectGranted = false,
        )
        try {
            startForeground(NOTIFICATION_ID, notification, wanted)
        } catch (refused: Exception) {
            if (wanted == base) {
                Log.w(TAG, "Cannot run in the foreground; stopping", refused)
                stopSelf()
                return
            }
            Log.w(TAG, "Foreground service type refused; keeping the base type", refused)
            runCatching { startForeground(NOTIFICATION_ID, notification, base) }
                .onFailure { failure ->
                    Log.w(TAG, "Cannot run in the foreground; stopping", failure)
                    stopSelf()
                }
        }
    }

    private fun holds(permission: String): Boolean = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Stops the service once no link has work and no command is in flight, after
     * [HUB_IDLE_GRACE_MS] of that. Any return to busy inside the grace cancels the stop.
     * stopSelf takes the last start id so that a start delivered after the decision, which
     * arrives with a newer id, keeps the service up instead of being lost.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stopWhenIdle() {
        combine(registry.states, inFlightCommands) { states, inFlight ->
            inFlight > 0 || hubHasWorkFor(states.values.map { it.link })
        }
            .distinctUntilChanged()
            .flatMapLatest { busy ->
                if (busy) {
                    emptyFlow()
                } else {
                    flow {
                        delay(HUB_IDLE_GRACE_MS)
                        emit(Unit)
                    }
                }
            }
            .onEach { stopSelf(lastStartId) }
            .launchIn(scope)
    }

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

    /**
     * Steps from the pattern CLumo's committed frame matches, so a press follows what is on
     * the matrix rather than the last selection this phone made. The recorded intent is the
     * fallback for a frame the library cannot name.
     */
    private suspend fun cycleDisplayPattern(connection: DeviceSession, forward: Boolean) {
        val deviceId = connection.deviceId.value
            ?: repository.getByAddress(connection.address)?.id
            ?: connection.address
        val shown = patternFor(connection.state.value.effectiveCommittedFrame, patterns.patterns.first())
        val next = patterns.cycleApplied(deviceId, shown?.id, forward) ?: return
        connection.commitPattern(next)
    }

    private suspend fun stepVisualizerSensitivity(up: Boolean) {
        val current = preferences.visualizerSensitivity.first()
        preferences.setVisualizerSensitivity(steppedVisualizerSensitivity(current, up))
    }
}
