package io.github.cespresso.clumo.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.cespresso.clumo.audio.AudioVisualizerManager
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.PomodoroStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per-device GATT connection and state for BLE protocol v2.
 * Owns its coroutine scope, audio visualizer, and reconnect job.
 * One instance per CLumo device; created and disposed by
 * [io.github.cespresso.clumo.data.DeviceRegistry].
 *
 * GATT only allows one outstanding operation, so reads/writes/subscribes go
 * through a small FIFO queue that advances on each completion callback.
 */
class DeviceConnection(
    private val context: Context,
    val address: String,
    initialName: String?,
) {

    companion object {
        private const val TAG = "DeviceConnection"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val BOND_TIMEOUT_MS = 45_000L
        private const val SYNC_TIMEOUT_MS = 12_000L
        private const val GATT_CACHE_REFRESH_DELAY_MS = 600L
        private const val AUDIO_SEND_INTERVAL_MS = 80L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- Public state ---

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _connectionFailure = MutableStateFlow<ConnectionFailure?>(null)
    val connectionFailure = _connectionFailure.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt = _reconnectAttempt.asStateFlow()

    private val _currentMode = MutableStateFlow<Int?>(null)
    val currentMode = _currentMode.asStateFlow()

    private val _pomodoroStatus = MutableStateFlow<PomodoroStatus?>(null)
    val pomodoroStatus = _pomodoroStatus.asStateFlow()

    private val _timerStatus = MutableStateFlow<CountdownTimerStatus?>(null)
    val timerStatus = _timerStatus.asStateFlow()

    private val _brightness = MutableStateFlow(0x0F)
    val brightness = _brightness.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId = _deviceId.asStateFlow()

    private val _deviceName = MutableStateFlow(initialName)
    val deviceName = _deviceName.asStateFlow()

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)
    val buttonEvents = _buttonEvents.asSharedFlow()

    val audioVisualizer = AudioVisualizerManager()

    // --- GATT operation queue ---

    private sealed class GattOp {
        class Subscribe(val uuid: UUID) : GattOp()
        class Read(val uuid: UUID) : GattOp()
        class Write(val uuid: UUID, val value: ByteArray, val noResponse: Boolean) : GattOp()
    }

    private val queueLock = Any()
    private val opQueue = ArrayDeque<GattOp>()
    private var opInFlight = false

    // --- Internals ---

    @Volatile private var gatt: BluetoothGatt? = null
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()
    private var audioSendJob: Job? = null
    private var reconnectJob: Job? = null
    private var phaseTimeoutJob: Job? = null
    private var reconnectAttempts = 0
    private var initialSync = false
    private var gattCacheRefreshAttempted = false
    @Volatile private var userDisconnect = false

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            if (device.address != address) return

            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

            when (state) {
                BluetoothDevice.BOND_BONDED -> {
                    if (_connectionState.value != ConnectionState.Bonding) return
                    phaseTimeoutJob?.cancel()
                    _connectionState.value = ConnectionState.Connected
                    val started = runCatching { gatt?.discoverServices() == true }.getOrDefault(false)
                    if (started) {
                        startPhaseTimeout(SYNC_TIMEOUT_MS, ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                    } else {
                        fail(ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                    }
                }
                BluetoothDevice.BOND_NONE -> if (prevState == BluetoothDevice.BOND_BONDING) {
                    if (_connectionState.value != ConnectionState.Bonding) return
                    Log.w(TAG, "$address: bonding failed")
                    fail(ConnectionFailure.PairingFailed, retry = false)
                }
            }
        }
    }

    init {
        context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    // --- Lifecycle ---

    @SuppressLint("MissingPermission")
    fun connect() {
        if (_connectionState.value != ConnectionState.Disconnected &&
            _connectionState.value != ConnectionState.Error
        ) return
        userDisconnect = false
        reconnectJob?.cancel()
        reconnectAttempts = 0
        gattCacheRefreshAttempted = false
        _reconnectAttempt.value = 0
        _connectionFailure.value = null
        closeGatt()
        startGattConnection(isReconnect = false)
    }

    @SuppressLint("MissingPermission")
    private fun startGattConnection(isReconnect: Boolean) {
        if (!hasConnectPermission()) {
            fail(ConnectionFailure.PermissionDenied, retry = false)
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: run {
            Log.w(TAG, "Bluetooth adapter not available")
            fail(ConnectionFailure.BluetoothUnavailable, retry = false)
            return
        }
        if (!adapter.isEnabled) {
            fail(ConnectionFailure.BluetoothDisabled, retry = false)
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrElse {
            Log.w(TAG, "$address: invalid Bluetooth address", it)
            fail(ConnectionFailure.ConnectionLost, retry = false)
            return
        }
        _connectionState.value = if (isReconnect) {
            ConnectionState.Reconnecting
        } else {
            ConnectionState.Connecting
        }
        val newGatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            Log.w(TAG, "$address: connectGatt failed", it)
            if (it is SecurityException) {
                fail(ConnectionFailure.PermissionDenied, retry = false)
            } else {
                fail(ConnectionFailure.ConnectionLost, retry = true)
            }
            return
        }
        gatt = newGatt
        startPhaseTimeout(CONNECT_TIMEOUT_MS, ConnectionFailure.ConnectionTimedOut, retry = true)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        phaseTimeoutJob?.cancel()
        phaseTimeoutJob = null
        stopAudioVisualizer()
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
        _connectionFailure.value = null
        _reconnectAttempt.value = 0
        _currentMode.value = null
        _pomodoroStatus.value = null
        _timerStatus.value = null
        _deviceId.value = null
    }

    fun dispose() {
        disconnect()
        runCatching { context.unregisterReceiver(bondReceiver) }
        scope.cancel()
    }

    private fun clearGattState() {
        initialSync = false
        characteristics.clear()
        synchronized(queueLock) {
            opQueue.clear()
            opInFlight = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect(reason: ConnectionFailure) {
        if (userDisconnect || reconnectJob?.isActive == true) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionFailure.value = reason
            _connectionState.value = ConnectionState.Error
            _reconnectAttempt.value = 0
            return
        }
        reconnectAttempts++
        _reconnectAttempt.value = reconnectAttempts
        _connectionFailure.value = reason
        _connectionState.value = ConnectionState.Reconnecting
        reconnectJob = scope.launch {
            delay(700L * reconnectAttempts)
            reconnectJob = null
            if (!userDisconnect) startGattConnection(isReconnect = true)
        }
    }

    private fun startPhaseTimeout(durationMs: Long, failure: ConnectionFailure, retry: Boolean) {
        phaseTimeoutJob?.cancel()
        phaseTimeoutJob = scope.launch {
            delay(durationMs)
            Log.w(TAG, "$address: phase timed out in ${_connectionState.value}")
            fail(failure, retry)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fail(failure: ConnectionFailure, retry: Boolean) {
        phaseTimeoutJob?.cancel()
        phaseTimeoutJob = null
        _connectionFailure.value = failure
        stopAudioVisualizer()
        closeGatt()
        if (retry) scheduleReconnect(failure) else _connectionState.value = ConnectionState.Error
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val old = gatt
        gatt = null
        clearGattState()
        if (old != null) {
            runCatching { old.disconnect() }
            runCatching { old.close() }
        }
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun refreshGattCache(g: BluetoothGatt): Boolean = runCatching {
        val refresh = g.javaClass.getMethod("refresh")
        refresh.invoke(g) as? Boolean == true
    }.onFailure {
        Log.w(TAG, "$address: GATT cache refresh failed", it)
    }.getOrDefault(false)

    // --- Queue machinery ---

    private fun enqueue(op: GattOp) {
        synchronized(queueLock) { opQueue.add(op) }
        processQueue()
    }

    @SuppressLint("MissingPermission")
    private fun processQueue() {
        val op = synchronized(queueLock) {
            if (opInFlight) return
            val next = opQueue.removeFirstOrNull()
            if (next == null) return@synchronized null
            opInFlight = true
            next
        }
        if (op == null) {
            maybeFinishInitialSync()
            return
        }
        val g = gatt
        val started = if (g == null) false else when (op) {
            is GattOp.Subscribe -> {
                val char = characteristics[op.uuid]
                val desc = char?.getDescriptor(BleUuids.CCCD)
                if (char != null && desc != null) {
                    g.setCharacteristicNotification(char, true)
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                } else false
            }
            is GattOp.Read -> {
                val char = characteristics[op.uuid]
                if (char != null) g.readCharacteristic(char) else false
            }
            is GattOp.Write -> {
                val char = characteristics[op.uuid]
                if (char != null) {
                    char.writeType = if (op.noResponse) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    @Suppress("DEPRECATION")
                    char.value = op.value
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(char)
                } else false
            }
        }
        if (!started) {
            synchronized(queueLock) { opInFlight = false }
            if (initialSync) {
                fail(ConnectionFailure.SynchronizationFailed, retry = true)
                return
            }
            // Skip the failed op and keep the queue moving.
            processQueue()
        }
    }

    private fun onOpComplete(success: Boolean = true) {
        synchronized(queueLock) { opInFlight = false }
        if (!success && initialSync) {
            fail(ConnectionFailure.SynchronizationFailed, retry = true)
            return
        }
        processQueue()
    }

    private fun maybeFinishInitialSync() {
        if (!initialSync) return
        val idle = synchronized(queueLock) { !opInFlight && opQueue.isEmpty() }
        if (!idle) return
        if (_deviceId.value == null || _currentMode.value == null ||
            _pomodoroStatus.value == null || _timerStatus.value == null
        ) {
            fail(ConnectionFailure.SynchronizationFailed, retry = true)
            return
        }
        initialSync = false
        phaseTimeoutJob?.cancel()
        phaseTimeoutJob = null
        reconnectAttempts = 0
        _reconnectAttempt.value = 0
        _connectionFailure.value = null
        _connectionState.value = ConnectionState.Ready
    }

    // --- Public actions ---

    fun writeMode(mode: Int) {
        if (shouldStopVisualizerForModeChange(audioVisualizer.isActive.value, mode)) {
            stopAudioVisualizer(clearDisplay = true)
        }
        enqueue(GattOp.Write(BleUuids.MODE, byteArrayOf(mode.toByte()), noResponse = false))
    }

    /**
     * Write 8 bytes to DISPLAY. When [stream] is true (visualizer frames,
     * live-preview edits) pending display writes are coalesced so only the
     * newest frame is sent, using write-without-response.
     */
    fun writeDisplay(data: ByteArray, stream: Boolean = false) {
        val payload = data.copyOf(8)
        synchronized(queueLock) {
            if (stream) opQueue.removeAll { it is GattOp.Write && it.uuid == BleUuids.DISPLAY }
            opQueue.add(GattOp.Write(BleUuids.DISPLAY, payload, noResponse = stream))
        }
        processQueue()
    }

    fun writePomodoroCommand(vararg bytes: Int) {
        enqueue(GattOp.Write(BleUuids.POMODORO, ByteArray(bytes.size) { bytes[it].toByte() }, noResponse = false))
    }

    fun pomodoroStart() = writePomodoroCommand(BleUuids.POMODORO_CMD_START)
    fun pomodoroPause() = writePomodoroCommand(BleUuids.POMODORO_CMD_PAUSE)
    fun pomodoroReset() = writePomodoroCommand(BleUuids.POMODORO_CMD_RESET)

    fun pomodoroSetDurations(workMin: Int, breakMin: Int) =
        writePomodoroCommand(BleUuids.POMODORO_CMD_SET_DURATIONS, workMin.coerceIn(1, 99), breakMin.coerceIn(1, 99))

    private fun writeTimerCommand(vararg bytes: Int) {
        enqueue(GattOp.Write(BleUuids.TIMER, ByteArray(bytes.size) { bytes[it].toByte() }, noResponse = false))
    }

    fun timerStart() = writeTimerCommand(BleUuids.TIMER_CMD_START)
    fun timerPause() = writeTimerCommand(BleUuids.TIMER_CMD_PAUSE)
    fun timerCancel() = writeTimerCommand(BleUuids.TIMER_CMD_CANCEL)

    fun timerSetDuration(minutes: Int, seconds: Int) {
        val payload = BleUuids.timerSetDurationPayloadOrNull(minutes, seconds) ?: return
        enqueue(GattOp.Write(BleUuids.TIMER, payload, noResponse = false))
    }

    fun writeBrightness(level: Int) {
        val clamped = level.coerceIn(0, 0x0F)
        _brightness.value = clamped
        enqueue(GattOp.Write(BleUuids.BRIGHTNESS, byteArrayOf(clamped.toByte()), noResponse = false))
    }

    fun readMode() = enqueue(GattOp.Read(BleUuids.MODE))
    fun readPomodoroStatus() = enqueue(GattOp.Read(BleUuids.POMODORO))
    fun readTimerStatus() = enqueue(GattOp.Read(BleUuids.TIMER))
    fun readBrightness() = enqueue(GattOp.Read(BleUuids.BRIGHTNESS))
    fun readDeviceId() = enqueue(GattOp.Read(BleUuids.DEVICE_ID))

    // --- Audio visualizer ---

    fun startAudioVisualizer(): Boolean {
        if (!canRunAudioVisualizer(_connectionState.value, _currentMode.value)) {
            Log.w(TAG, "$address: audio visualizer requires ready Visualizer mode")
            return false
        }
        if (!audioVisualizer.start()) {
            Log.w(TAG, "$address: audio visualizer failed to start")
            return false
        }
        audioSendJob?.cancel()
        audioSendJob = scope.launch {
            while (true) {
                delay(AUDIO_SEND_INTERVAL_MS)
                if (!canRunAudioVisualizer(_connectionState.value, _currentMode.value)) continue
                val columns = audioVisualizer.columns.value
                writeDisplay(ByteArray(8) { columns[it].toByte() }, stream = true)
            }
        }
        return true
    }

    fun stopAudioVisualizer(clearDisplay: Boolean = false) {
        audioSendJob?.cancel()
        audioSendJob = null
        audioVisualizer.stop()
        if (clearDisplay && canRunAudioVisualizer(_connectionState.value, _currentMode.value)) {
            writeDisplay(ByteArray(8), stream = true)
        }
    }

    // --- GATT callback ---

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) {
                runCatching { g.close() }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    phaseTimeoutJob?.cancel()
                    _deviceName.value = g.device.name ?: _deviceName.value
                    if (g.device.bondState == BluetoothDevice.BOND_BONDED) {
                        _connectionState.value = ConnectionState.Connected
                        val started = runCatching { g.discoverServices() }.getOrDefault(false)
                        if (started) {
                            startPhaseTimeout(SYNC_TIMEOUT_MS, ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                        } else {
                            fail(ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                        }
                    } else {
                        // System pairing dialog will prompt for the passkey (123456).
                        _connectionState.value = ConnectionState.Bonding
                        val started = runCatching { g.device.createBond() }.getOrDefault(false)
                        if (started) {
                            startPhaseTimeout(BOND_TIMEOUT_MS, ConnectionFailure.PairingFailed, retry = false)
                        } else {
                            fail(ConnectionFailure.PairingFailed, retry = false)
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "$address: connection lost (status=$status)")
                    phaseTimeoutJob?.cancel()
                    gatt = null
                    runCatching { g.close() }
                    clearGattState()
                    stopAudioVisualizer()
                    if (!userDisconnect) {
                        scheduleReconnect(ConnectionFailure.ConnectionLost)
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "$address: service discovery failed ($status)")
                fail(ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                return
            }
            val service = g.getService(BleUuids.SERVICE)
            val required = setOf(
                BleUuids.MODE, BleUuids.DISPLAY, BleUuids.POMODORO, BleUuids.TIMER,
                BleUuids.BRIGHTNESS, BleUuids.DEVICE_ID,
            )
            // Absent on firmware older than the button-roles release, so its absence
            // must not reject the device — but it does warrant one cache refresh.
            val optional = setOf(BleUuids.BUTTON)
            val discovered = service?.characteristics
                ?.mapTo(mutableSetOf()) { it.uuid }
                .orEmpty()
            Log.d(
                TAG,
                "$address: discovered CLumo characteristics=" +
                    discovered.joinToString(),
            )
            when (gattCompatibilityAction(discovered, required, optional, gattCacheRefreshAttempted)) {
                GattCompatibilityAction.ACCEPT -> Unit
                GattCompatibilityAction.REFRESH_CACHE -> {
                    gattCacheRefreshAttempted = true
                    if (!refreshGattCache(g)) {
                        // A refresh we could not perform is only fatal when something
                        // required is absent. Firmware that genuinely predates an
                        // optional characteristic still works without it.
                        if (!discovered.containsAll(required)) {
                            Log.w(TAG, "$address: required CLumo characteristics are missing")
                            fail(ConnectionFailure.IncompatibleDevice, retry = false)
                            return
                        }
                        Log.w(TAG, "$address: could not refresh GATT cache; continuing without optional characteristics")
                    } else {
                        Log.i(TAG, "$address: stale GATT cache cleared; rediscovering services")
                        startPhaseTimeout(
                            SYNC_TIMEOUT_MS,
                            ConnectionFailure.ServiceDiscoveryFailed,
                            retry = true,
                        )
                        scope.launch {
                            delay(GATT_CACHE_REFRESH_DELAY_MS)
                            if (g !== gatt) return@launch
                            val started = runCatching { g.discoverServices() }.getOrDefault(false)
                            if (!started) {
                                fail(ConnectionFailure.ServiceDiscoveryFailed, retry = true)
                            }
                        }
                        return
                    }
                }
                GattCompatibilityAction.REJECT -> {
                    Log.w(
                        TAG,
                        if (service == null) "$address: CLumo service not found"
                        else "$address: required CLumo characteristics are missing",
                    )
                    fail(ConnectionFailure.IncompatibleDevice, retry = false)
                    return
                }
            }

            val compatibleService = service ?: return
            characteristics.clear()
            required.forEach { uuid ->
                compatibleService.getCharacteristic(uuid)?.let { characteristics[uuid] = it }
            }
            // Optional so a device on older firmware still connects.
            val hasButton = compatibleService.getCharacteristic(BleUuids.BUTTON)
                ?.also { characteristics[BleUuids.BUTTON] = it } != null

            _connectionState.value = ConnectionState.Synchronizing
            _deviceId.value = null
            _currentMode.value = null
            _pomodoroStatus.value = null
            _timerStatus.value = null
            initialSync = true
            startPhaseTimeout(SYNC_TIMEOUT_MS, ConnectionFailure.SynchronizationFailed, retry = true)

            // Subscribe to server notifications, then load initial state.
            enqueue(GattOp.Subscribe(BleUuids.MODE))
            enqueue(GattOp.Subscribe(BleUuids.POMODORO))
            enqueue(GattOp.Subscribe(BleUuids.TIMER))
            enqueue(GattOp.Subscribe(BleUuids.BRIGHTNESS))
            if (hasButton) enqueue(GattOp.Subscribe(BleUuids.BUTTON))
            readDeviceId()
            readMode()
            readPomodoroStatus()
            readTimerStatus()
            readBrightness()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt) return
            onOpComplete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                char.value?.let { handleValue(char.uuid, it) }
            }
            onOpComplete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The device may not notify on app-initiated changes; re-read to stay in sync.
                when (char.uuid) {
                    BleUuids.MODE -> readMode()
                    BleUuids.POMODORO -> readPomodoroStatus()
                    BleUuids.TIMER -> readTimerStatus()
                    else -> Unit
                }
            }
            onOpComplete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
            if (g !== gatt) return
            char.value?.let { handleValue(char.uuid, it) }
        }
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return
        when (uuid) {
            BleUuids.MODE -> _currentMode.value = value[0].toInt() and 0xFF
            BleUuids.POMODORO -> PomodoroStatus.parse(value)?.let { _pomodoroStatus.value = it }
            BleUuids.TIMER -> CountdownTimerStatus.parse(value)?.let { _timerStatus.value = it }
            BleUuids.BRIGHTNESS -> _brightness.value = value[0].toInt() and 0xFF
            BleUuids.DEVICE_ID -> _deviceId.value = formatDeviceId(value)
            BleUuids.BUTTON -> ButtonEvent.parse(value)?.let {
                if (!_buttonEvents.tryEmit(it)) {
                    Log.w(TAG, "$address: button event dropped, buffer full")
                }
            }
        }
    }

    private fun formatDeviceId(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        if (bytes.size != 16) return hex
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
