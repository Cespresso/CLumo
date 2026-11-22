package io.github.cespresso.clumo.ui.components.product

/** Main-thread gate: duplicate requests coalesce; obsolete completions cannot navigate. */
internal class CloseRequest {
    private var generation = 0L
    private var pending: Pair<Long, () -> Unit>? = null

    fun request(onClosed: () -> Unit): Long {
        pending?.let { return it.first }
        return (++generation).also { pending = it to onClosed }
    }

    fun cancel() {
        pending = null
    }

    fun complete(token: Long) {
        val request = pending?.takeIf { it.first == token } ?: return
        pending = null // Clear before callback, including reentrant navigation.
        request.second()
    }
}

/** A single timeline runs forward or backward without restarting when its target changes. */
internal class ReversibleReveal(private val duration: Float) {
    var progress = 0f
        private set

    fun reset() {
        progress = 0f
    }

    fun advance(seconds: Float, opening: Boolean) {
        progress = (progress + seconds.coerceAtLeast(0f) / duration * if (opening) 1f else -1f).coerceIn(0f, 1f)
    }
}

/** Camera reaches the destination slightly before the ribbon finishes, in either direction. */
internal object AssignmentMotion {
    const val DURATION = .9f
    fun cameraFraction(progress: Float) = (progress * DURATION / .7f).coerceIn(0f, 1f)
}
