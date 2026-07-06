package io.github.cespresso.clumo.domain

enum class ConnectionState {
    Disconnected,
    Connecting,
    Bonding,
    Connected,
    Ready,
    Error,
}
