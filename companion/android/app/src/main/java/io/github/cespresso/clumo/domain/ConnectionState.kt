package io.github.cespresso.clumo.domain

enum class ConnectionState {
    Disconnected,
    Connecting,
    Reconnecting,
    Bonding,
    Connected,
    Synchronizing,
    Ready,
    Error,
}

/** User-actionable reason why a connection attempt stopped. */
enum class ConnectionFailure {
    BluetoothUnavailable,
    BluetoothDisabled,
    PermissionDenied,
    ConnectionTimedOut,
    PairingFailed,
    ServiceDiscoveryFailed,
    IncompatibleDevice,
    SynchronizationFailed,
    ConnectionLost,
}
