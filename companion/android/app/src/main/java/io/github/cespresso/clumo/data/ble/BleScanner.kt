package io.github.cespresso.clumo.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.cespresso.clumo.domain.DeviceAdvertisement
import io.github.cespresso.clumo.domain.ScanEvent
import io.github.cespresso.clumo.domain.ScanFailure
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Continuous scan for CLumo devices, filtered by the CLumo service UUID.
 * Emits each advertisement seen; the caller de-duplicates.
 */
interface DeviceScanner {
    fun scan(): Flow<ScanEvent>
}

class BleScanner(private val context: Context) : DeviceScanner {

    companion object {
        private const val TAG = "BleScanner"
    }

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<ScanEvent> =
        callbackFlow {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter = try {
                manager?.adapter
            } catch (_: SecurityException) {
                trySend(ScanEvent.Failed(ScanFailure.PermissionDenied))
                close()
                return@callbackFlow
            }
            if (adapter == null) {
                trySend(ScanEvent.Failed(ScanFailure.BluetoothUnavailable))
                close()
                return@callbackFlow
            }
            val scanner = try {
                if (!adapter.isEnabled) {
                    trySend(ScanEvent.Failed(ScanFailure.BluetoothDisabled))
                    close()
                    return@callbackFlow
                }
                adapter.bluetoothLeScanner
            } catch (_: SecurityException) {
                trySend(ScanEvent.Failed(ScanFailure.PermissionDenied))
                close()
                return@callbackFlow
            }
            if (scanner == null) {
                Log.w(TAG, "BLE scanner not available")
                trySend(ScanEvent.Failed(ScanFailure.BluetoothUnavailable))
                close()
                return@callbackFlow
            }

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    trySend(
                        ScanEvent.DeviceFound(
                            DeviceAdvertisement(
                                address = result.device.address,
                                name = result.device.name ?: result.scanRecord?.deviceName,
                                rssi = result.rssi,
                            ),
                        ),
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "Scan failed: $errorCode")
                    trySend(ScanEvent.Failed(ScanFailure.ScanFailed))
                    close()
                }
            }

            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build(),
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            try {
                scanner.startScan(filters, settings, callback)
            } catch (e: SecurityException) {
                Log.w(TAG, "Scan permission denied", e)
                trySend(ScanEvent.Failed(ScanFailure.PermissionDenied))
                close()
                return@callbackFlow
            }
            Log.d(TAG, "Scan started")

            awaitClose {
                runCatching { scanner.stopScan(callback) }
                Log.d(TAG, "Scan stopped")
            }
        }
}
