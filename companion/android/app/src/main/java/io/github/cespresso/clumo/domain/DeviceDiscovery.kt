package io.github.cespresso.clumo.domain

/** A CLumo advertisement observed before its stable firmware id can be read over GATT. */
data class DeviceAdvertisement(
    val address: String,
    val name: String?,
    val rssi: Int,
)

sealed interface ScanEvent {
    data class DeviceFound(val advertisement: DeviceAdvertisement) : ScanEvent
    data class Failed(val reason: ScanFailure) : ScanEvent
}

enum class ScanFailure {
    BluetoothUnavailable,
    BluetoothDisabled,
    PermissionDenied,
    ScanFailed,
}
