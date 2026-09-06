package io.github.cespresso.clumo.domain

/** A command sent to CLumo whose canonical read/notification has not arrived yet. */
data class PendingCommand<T>(
    val value: T,
    val sentAtRealtime: Long,
)

data class PendingCommands(
    val mode: PendingCommand<Int>? = null,
    val brightnessLevel: PendingCommand<Int>? = null,
    /** A committed Display frame, as face bits, still awaiting the device's confirmation. */
    val committedFrame: PendingCommand<Long>? = null,
) {
    fun expire(nowRealtime: Long, ttlMs: Long = DEFAULT_TTL_MS): PendingCommands =
        copy(
            mode = mode?.takeIf { nowRealtime - it.sentAtRealtime < ttlMs },
            brightnessLevel = brightnessLevel
                ?.takeIf { nowRealtime - it.sentAtRealtime < ttlMs },
            committedFrame = committedFrame?.takeIf { nowRealtime - it.sentAtRealtime < ttlMs },
        )

    companion object {
        const val DEFAULT_TTL_MS = 3_000L
    }
}
