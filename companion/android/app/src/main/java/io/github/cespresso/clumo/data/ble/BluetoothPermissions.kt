package io.github.cespresso.clumo.data.ble

import android.Manifest
import android.os.Build

/**
 * Runtime permissions needed for scanning and connecting, per SDK level. Before Android 12 a
 * BLE scan could reveal the user's location, so the platform asked for that instead.
 */
fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
