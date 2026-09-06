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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A BLE advertisement of a CLumo device observed during scanning.
 * The stable device id is unknown at scan time (only readable after GATT connect),
 * so only MAC + advertised name are available here.
 */
data class DeviceAdvertisement(
    val address: String,
    val name: String?,
    val rssi: Int,
)

/**
 * Continuous scan for CLumo devices, filtered by the CLumo service UUID.
 * Emits each advertisement seen; the caller de-duplicates.
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"
    }

    @SuppressLint("MissingPermission")
    fun scan(): Flow<DeviceAdvertisement> = callbackFlow {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE scanner not available")
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    DeviceAdvertisement(
                        address = result.device.address,
                        name = result.device.name ?: result.scanRecord?.deviceName,
                        rssi = result.rssi,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed: $errorCode")
                close()
            }
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(filters, settings, callback)
        Log.d(TAG, "Scan started")

        awaitClose {
            runCatching { scanner.stopScan(callback) }
            Log.d(TAG, "Scan stopped")
        }
    }
}
