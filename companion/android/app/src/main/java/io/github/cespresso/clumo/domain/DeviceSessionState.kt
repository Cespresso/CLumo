package io.github.cespresso.clumo.domain

/** A device session's reliable state: what CLumo confirmed, plus what is still in flight. */
data class DeviceSessionState(
    val link: ConnectionState,
    val observed: DeviceSnapshot? = null,
    val pending: PendingCommands = PendingCommands(),
    val failure: ConnectionFailure? = null,
    val reconnectAttempt: Int = 0,
) {
    val effectiveMode: Int
        get() = effectiveModeOf(pending.mode?.value, observed?.mode)

    val effectiveBrightnessLevel: Int?
        get() = pending.brightnessLevel?.value ?: observed?.brightnessLevel

    /** The Display frame to draw: a just-sent commit until CLumo reads its own back. */
    val effectiveCommittedFrame: Long?
        get() = pending.committedFrame?.value ?: observed?.committedFrame
}
