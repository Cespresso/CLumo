package io.github.cespresso.clumo.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.Device
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceNaming
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.resolvePrimaryTarget
import io.github.cespresso.clumo.widget.HEARTBEAT_INTERVAL_MS
import io.github.cespresso.clumo.widget.WidgetBlock
import io.github.cespresso.clumo.widget.WidgetSnapshot
import io.github.cespresso.clumo.widget.WidgetSnapshotFactory
import io.github.cespresso.clumo.widget.WidgetSnapshotStore
import io.github.cespresso.clumo.widget.sameContentAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Turns the primary device's live state into the single snapshot both widgets read. The
 * firmware notifies once per second and the face has 64 steps, so publishing only on a
 * visible change caps a timed phase at 64 updates however long it runs.
 */
class WidgetStatePublisher(
    private val context: Context,
    private val registry: DeviceSessionRegistry,
    private val repository: DeviceRepository,
    private val preferences: AppPreferences,
    private val patterns: PatternRepository,
    private val store: WidgetSnapshotStore,
    private val onPublished: suspend () -> Unit,
) {

    private companion object {
        const val TAG = "WidgetStatePublisher"
    }

    private val commandFailed = MutableStateFlow(false)

    /**
     * A blocked layout carries no button, so a widget stuck on "Bluetooth is off" cannot
     * recover by itself. Holding the block in a flow is what lets an adapter coming back on
     * move it, since nothing else the publisher watches changes at that moment.
     */
    private val block = MutableStateFlow<WidgetBlock?>(null)
    private var lastPublished: WidgetSnapshot? = null

    fun setCommandFailed(failed: Boolean) {
        commandFailed.value = failed
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        // Start from real platform state rather than the "not blocked" placeholder above.
        refreshBlock()

        val identity: Flow<Identity> = combine(
            repository.devices,
            preferences.primaryDeviceId,
            preferences.aliases,
            preferences.deviceAppearances,
        ) { devices, primaryId, aliases, appearances ->
            Identity(resolvePrimaryTarget(primaryId, devices), aliases, appearances)
        }

        // Flatten into the primary session's own state, so a status notification
        // moves the widget without the session map itself changing.
        combine(
            identity,
            registry.sessions,
            patterns.patterns,
            patterns.appliedPatternIds,
        ) { id, sessions, patternList, appliedPatternIds ->
            val selectedId = id.target?.id?.let(appliedPatternIds::get)
            val selected = patternList.firstOrNull { it.id == selectedId }
            val pattern = PatternSelection(
                name = selected?.name,
                bits = selected?.let { FaceBits.fromBitsString(it.bits) } ?: FaceBits.EMPTY,
            )
            Triple(id, id.target?.let { sessions[it.address] }, pattern)
        }
            .flatMapLatest { (id, session, pattern) ->
                if (session == null) {
                    flowOf(Live(id, pattern, ConnectionState.Disconnected, null, null, null))
                } else {
                    session.state.map { state ->
                        Live(
                            identity = id,
                            pattern = pattern,
                            connectionState = state.link,
                            mode = state.observed?.mode,
                            pomodoro = state.observed?.pomodoro,
                            timer = state.observed?.timer,
                        )
                    }
                }
            }
            .combine(commandFailed) { live, failed -> live to failed }
            // Combined in for its edges only. publish() re-reads the value.
            .combine(block) { pair, _ -> pair }
            .onEach { (live, failed) -> publish(live, failed) }
            .launchIn(scope)

        // Turning the adapter off tears the connection down, which the flows above report on
        // their own. Turning it back on changes nothing they watch.
        bluetoothStateChanges()
            .onEach { refreshBlock() }
            .launchIn(scope)

        scope.launch { heartbeat() }
    }

    /** Emits on every adapter transition. Only the trigger matters, never the value. */
    private fun bluetoothStateChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * Re-reads platform state. [block] conflates, so an unchanged block costs nothing. Every
     * caller sits outside a [guarded] body, so a failed read must not throw; it leaves the
     * previous value standing, which on the first read means "not blocked".
     */
    private fun refreshBlock() {
        runCatching { block.value = currentBlock() }
            .onFailure { Log.w(TAG, "Widget block refresh failed", it) }
    }

    /**
     * An idle or paused device legitimately produces no updates for hours, which would make a
     * healthy snapshot look stale. Rewriting the timestamp keeps it fresh without calling
     * [onPublished], since nothing visible moved.
     */
    private suspend fun heartbeat() {
        while (true) {
            delay(HEARTBEAT_INTERVAL_MS)
            refreshBlock()
            val current = lastPublished ?: continue
            // One failed beat must not end the loop for the service's lifetime.
            guarded("heartbeat") {
                store.write(current.copy(updatedAtRealtime = SystemClock.elapsedRealtime()))
            }
        }
    }

    /**
     * Publishes the state of a widget with no device to act on. A command that resolves no
     * target has nothing to execute, and without this the tap would look dead.
     */
    suspend fun publishNoTarget() {
        refreshBlock()
        guarded("publishNoTarget") {
            emit(
                WidgetSnapshotFactory.create(
                    target = null,
                    block = block.value,
                    connectionState = ConnectionState.Disconnected,
                    mode = null,
                    pomodoro = null,
                    timer = null,
                    patternName = null,
                    patternBits = FaceBits.EMPTY,
                    alias = "",
                    appearance = DeviceAppearance.DEFAULT,
                    commandFailed = false,
                    nowRealtime = SystemClock.elapsedRealtime(),
                )
            )
        }
    }

    private suspend fun publish(live: Live, failedFlag: Boolean) {
        // Granting BLUETOOTH_CONNECT produces no event, so any emission at all is the first
        // chance to notice it. Reading here rather than trusting the combined value also
        // keeps a frame from being written with a block this pass already knows is wrong.
        refreshBlock()
        guarded("publish") {
            val ready = live.connectionState == ConnectionState.Ready
            // A Ready link settles the flag whatever produced it, including a connection made
            // from the app. The presence widget has no button that could ever clear it.
            if (ready && failedFlag) commandFailed.value = false
            val target = live.identity.target
            emit(
                WidgetSnapshotFactory.create(
                    target = target,
                    block = block.value,
                    connectionState = live.connectionState,
                    mode = live.mode,
                    pomodoro = live.pomodoro,
                    timer = live.timer,
                    patternName = live.pattern.name,
                    patternBits = live.pattern.bits,
                    alias = target?.let {
                        DeviceNaming.displayName(
                            deviceId = it.id,
                            aliases = live.identity.aliases,
                            fallbackName = it.fallbackName,
                        )
                    }.orEmpty(),
                    appearance = target?.let { live.identity.appearances[it.id] }
                        ?: DeviceAppearance.DEFAULT,
                    // Waiting for the cleared flag to come back around the flow would draw one
                    // frame of "Can't connect" over a working link first.
                    commandFailed = failedFlag && !ready,
                    nowRealtime = SystemClock.elapsedRealtime(),
                )
            )
        }
    }

    /** Stores [snapshot] and moves the widgets, unless nothing visible changed. */
    private suspend fun emit(snapshot: WidgetSnapshot) {
        val previous = lastPublished
        if (previous != null && previous.sameContentAs(snapshot)) return
        // A failed write leaves lastPublished behind, so the next emission still counts as a
        // change and retries instead of deduplicating itself away.
        if (!store.write(snapshot)) return
        lastPublished = snapshot
        onPublished()
    }

    /**
     * A single failure costs one update. Letting it escape would unwind through `onEach`
     * and cancel the collector, which would stop widget updates for good.
     */
    private suspend fun guarded(what: String, body: suspend () -> Unit) {
        try {
            body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget $what failed", e)
        }
    }

    private fun currentBlock(): WidgetBlock? {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) return WidgetBlock.PermissionMissing
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return WidgetBlock.BluetoothOff
        return null
    }

    private data class Identity(
        val target: Device?,
        val aliases: Map<String, String>,
        val appearances: Map<String, DeviceAppearance>,
    )

    private data class Live(
        val identity: Identity,
        val pattern: PatternSelection,
        val connectionState: ConnectionState,
        val mode: Int?,
        val pomodoro: io.github.cespresso.clumo.domain.PomodoroStatus?,
        val timer: io.github.cespresso.clumo.domain.CountdownTimerStatus?,
    )

    data class PatternSelection(val name: String?, val bits: Long)
}
