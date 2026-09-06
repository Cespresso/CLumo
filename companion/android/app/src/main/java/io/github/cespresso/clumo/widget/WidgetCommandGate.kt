package io.github.cespresso.clumo.widget

import io.github.cespresso.clumo.domain.ConnectionState

enum class GateDecision {
    Execute,
    ConnectThenExecute,
    Refuse,
}

/**
 * What to do with a command that arrives while the link is down. A powered-off CLumo
 * must not attract repeated connection attempts, so a failure starts a cooldown that
 * only an explicit retry may bypass.
 */
object WidgetCommandGate {

    const val COOLDOWN_MS: Long = 30_000L
    const val CONNECT_TIMEOUT_MS: Long = 20_000L

    fun decide(
        command: WidgetCommand,
        connectionState: ConnectionState,
        lastFailureRealtime: Long?,
        nowRealtime: Long,
    ): GateDecision {
        if (connectionState == ConnectionState.Ready) return GateDecision.Execute
        if (command == WidgetCommand.Retry) return GateDecision.ConnectThenExecute
        if (lastFailureRealtime != null && inCooldown(lastFailureRealtime, nowRealtime)) {
            return GateDecision.Refuse
        }
        return GateDecision.ConnectThenExecute
    }

    fun hasTimedOut(waitStartedRealtime: Long, nowRealtime: Long): Boolean =
        nowRealtime - waitStartedRealtime >= CONNECT_TIMEOUT_MS

    /**
     * A reboot resets elapsedRealtime, which would leave a stored failure timestamp in
     * the future. Treat that as no cooldown rather than locking the user out.
     */
    private fun inCooldown(lastFailureRealtime: Long, nowRealtime: Long): Boolean {
        val elapsed = nowRealtime - lastFailureRealtime
        return elapsed in 0 until COOLDOWN_MS
    }
}
