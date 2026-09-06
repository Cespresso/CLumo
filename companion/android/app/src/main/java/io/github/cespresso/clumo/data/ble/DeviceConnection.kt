package io.github.cespresso.clumo.data.ble

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
import android.util.Log
import io.github.cespresso.clumo.audio.AudioVisualizerManager
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.TimerStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per-device GATT connection and state for BLE protocol v1.
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
        private const val RECONNECT_WINDOW_MS = 10_000L
        private const val AUDIO_SEND_INTERVAL_MS = 80L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- Public state ---

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _currentMode = MutableStateFlow<Int?>(null)
    val currentMode = _currentMode.asStateFlow()

    private val _timerStatus = MutableStateFlow<TimerStatus?>(null)
    val timerStatus = _timerStatus.asStateFlow()

    private val _brightness = MutableStateFlow(0x0F)
    val brightness = _brightness.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId = _deviceId.asStateFlow()

    private val _deviceName = MutableStateFlow(initialName)
    val deviceName = _deviceName.asStateFlow()

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

    private var gatt: BluetoothGatt? = null
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()
    private var audioSendJob: Job? = null
    private var reconnectJob: Job? = null
    private var userDisconnect = false

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            if (device.address != address) return

            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

            when (state) {
                BluetoothDevice.BOND_BONDED -> {
                    _connectionState.value = ConnectionState.Connected
                    gatt?.discoverServices()
                }
                BluetoothDevice.BOND_NONE -> if (prevState == BluetoothDevice.BOND_BONDING) {
                    Log.w(TAG, "$address: bonding failed")
                    disconnect()
                    _connectionState.value = ConnectionState.Error
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
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: run {
            Log.w(TAG, "Bluetooth adapter not available")
            _connectionState.value = ConnectionState.Error
            return
        }
        val device = adapter.getRemoteDevice(address)
        _connectionState.value = ConnectionState.Connecting
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopAudioVisualizer()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        clearGattState()
        _connectionState.value = ConnectionState.Disconnected
        _currentMode.value = null
        _timerStatus.value = null
        _deviceId.value = null
    }

    fun dispose() {
        disconnect()
        runCatching { context.unregisterReceiver(bondReceiver) }
        scope.cancel()
    }

    private fun clearGattState() {
        characteristics.clear()
        synchronized(queueLock) {
            opQueue.clear()
            opInFlight = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (_connectionState.value != ConnectionState.Ready && !userDisconnect) {
                if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                    _connectionState.value = ConnectionState.Error
                    break
                }
                attempt++
                _connectionState.value = ConnectionState.Connecting
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: break
                val device = adapter.getRemoteDevice(address)
                device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                delay(RECONNECT_WINDOW_MS)
                if (_connectionState.value == ConnectionState.Ready || userDisconnect) break
                gatt?.close()
                gatt = null
            }
            reconnectJob = null
        }
    }

    // --- Queue machinery ---

    private fun enqueue(op: GattOp) {
        synchronized(queueLock) { opQueue.add(op) }
        processQueue()
    }

    @SuppressLint("MissingPermission")
    private fun processQueue() {
        val op: GattOp
        synchronized(queueLock) {
            if (opInFlight) return
            op = opQueue.removeFirstOrNull() ?: return
            opInFlight = true
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
            // Skip the failed op and keep the queue moving.
            processQueue()
        }
    }

    private fun onOpComplete() {
        synchronized(queueLock) { opInFlight = false }
        processQueue()
    }

    // --- Public actions ---

    fun writeMode(mode: Int) {
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

    fun writeTimerCommand(vararg bytes: Int) {
        enqueue(GattOp.Write(BleUuids.TIMER, ByteArray(bytes.size) { bytes[it].toByte() }, noResponse = false))
    }

    fun timerStart() = writeTimerCommand(BleUuids.TIMER_CMD_START)
    fun timerPause() = writeTimerCommand(BleUuids.TIMER_CMD_PAUSE)
    fun timerReset() = writeTimerCommand(BleUuids.TIMER_CMD_RESET)

    fun timerSetDurations(workMin: Int, breakMin: Int) =
        writeTimerCommand(BleUuids.TIMER_CMD_SET_DURATIONS, workMin.coerceIn(1, 99), breakMin.coerceIn(1, 99))

    fun writeBrightness(level: Int) {
        val clamped = level.coerceIn(0, 0x0F)
        _brightness.value = clamped
        enqueue(GattOp.Write(BleUuids.BRIGHTNESS, byteArrayOf(clamped.toByte()), noResponse = false))
    }

    fun readMode() = enqueue(GattOp.Read(BleUuids.MODE))
    fun readTimerStatus() = enqueue(GattOp.Read(BleUuids.TIMER))
    fun readBrightness() = enqueue(GattOp.Read(BleUuids.BRIGHTNESS))
    fun readDeviceId() = enqueue(GattOp.Read(BleUuids.DEVICE_ID))

    // --- Audio visualizer ---

    fun startAudioVisualizer(): Boolean {
        if (!audioVisualizer.start()) {
            Log.w(TAG, "$address: audio visualizer failed to start")
            return false
        }
        audioSendJob?.cancel()
        audioSendJob = scope.launch {
            while (true) {
                delay(AUDIO_SEND_INTERVAL_MS)
                if (_connectionState.value != ConnectionState.Ready) continue
                val columns = audioVisualizer.columns.value
                writeDisplay(ByteArray(8) { columns[it].toByte() }, stream = true)
            }
        }
        return true
    }

    fun stopAudioVisualizer() {
        audioSendJob?.cancel()
        audioSendJob = null
        audioVisualizer.stop()
    }

    // --- GATT callback ---

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    _deviceName.value = g.device.name ?: _deviceName.value
                    if (g.device.bondState == BluetoothDevice.BOND_BONDED) {
                        _connectionState.value = ConnectionState.Connected
                        g.discoverServices()
                    } else {
                        // System pairing dialog will prompt for the passkey (123456).
                        _connectionState.value = ConnectionState.Bonding
                        g.device.createBond()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "$address: connection lost (status=$status)")
                    g.close()
                    gatt = null
                    clearGattState()
                    stopAudioVisualizer()
                    if (!userDisconnect) {
                        attemptReconnect()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "$address: service discovery failed ($status)")
                _connectionState.value = ConnectionState.Error
                return
            }
            val service = g.getService(BleUuids.SERVICE) ?: run {
                Log.w(TAG, "$address: CLumo service not found")
                _connectionState.value = ConnectionState.Error
                return
            }
            characteristics.clear()
            listOf(
                BleUuids.MODE, BleUuids.DISPLAY, BleUuids.TIMER,
                BleUuids.BRIGHTNESS, BleUuids.DEVICE_ID,
            ).forEach { uuid ->
                service.getCharacteristic(uuid)?.let { characteristics[uuid] = it }
            }

            _connectionState.value = ConnectionState.Ready

            // Subscribe to server notifications, then load initial state.
            enqueue(GattOp.Subscribe(BleUuids.MODE))
            enqueue(GattOp.Subscribe(BleUuids.TIMER))
            enqueue(GattOp.Subscribe(BleUuids.BRIGHTNESS))
            readDeviceId()
            readMode()
            readTimerStatus()
            readBrightness()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            onOpComplete()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                char.value?.let { handleValue(char.uuid, it) }
            }
            onOpComplete()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The device may not notify on app-initiated changes; re-read to stay in sync.
                when (char.uuid) {
                    BleUuids.MODE -> readMode()
                    BleUuids.TIMER -> readTimerStatus()
                    else -> Unit
                }
            }
            onOpComplete()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
            char.value?.let { handleValue(char.uuid, it) }
        }
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return
        when (uuid) {
            BleUuids.MODE -> _currentMode.value = value[0].toInt() and 0xFF
            BleUuids.TIMER -> TimerStatus.parse(value)?.let { _timerStatus.value = it }
            BleUuids.BRIGHTNESS -> _brightness.value = value[0].toInt() and 0xFF
            BleUuids.DEVICE_ID -> _deviceId.value = formatDeviceId(value)
        }
    }

    private fun formatDeviceId(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        if (bytes.size != 16) return hex
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
