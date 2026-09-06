package io.github.cespresso.clumo.ui.editor

import java.util.concurrent.CancellationException

internal suspend fun runPatternEditorOperation(
    persist: suspend () -> Unit,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit,
) {
    try {
        persist()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        onFailure(error)
        return
    }
    onSuccess()
}
