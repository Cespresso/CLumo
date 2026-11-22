package io.github.cespresso.clumo.ui.components.product

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI and renderer share descriptions, never Filament resources or a device connection. */
data class FloorSettingItem(val id: String, val title: String, val value: String = "", val selected: Boolean = false, val icon: String? = null)

data class FloorSettingsSnapshot(
    val part: StandPart? = null,
    val title: String = "部品をタップして設定",
    val status: String = "",
    val items: List<FloorSettingItem> = emptyList(),
    val overviewAssignments: Map<StandPart, String> = emptyMap(),
    val brightness: Float? = null,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val choosing: Boolean = false,
    val bindingIndex: Int? = null,
    val saveStatus: String? = null,
    val saveFailed: Boolean = false,
)

@Stable
class FloorSettingsState(val floorUi: Boolean = true) {
    var destination by mutableStateOf<DeviceDestination>(DeviceDestination.Detail)
        private set

    fun selectPart(part: StandPart) {
        cancelClose()
        destination = DeviceDestination.Part(part)
    }

    fun selectBinding(index: Int) {
        val part = destination.part ?: return
        if (index !in part.bindingIndices || closeToken != null || assignmentCloseToken != null) return
        destination = DeviceDestination.Assignment(part, index)
    }

    /** True consumes back, including a repeated request during a closing animation. */
    fun navigateBack(after: () -> Unit = {}): Boolean {
        if (closeToken != null || assignmentCloseToken != null) return true
        val from = destination
        val parent = from.parent() ?: return false
        val finish = {
            if (destination == from) {
                destination = parent
                after()
            }
        }
        when {
            !floorUi -> finish()
            from is DeviceDestination.Assignment -> requestAssignmentClose(finish)
            else -> requestClose(finish)
        }
        return true
    }

    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<FloorSettingsState, String>(
            save = { listOf(it.floorUi.toString(), it.destination.part?.name.orEmpty(), it.destination.bindingIndex?.toString().orEmpty()) },
            restore = { saved ->
                FloorSettingsState(saved[0].toBoolean()).apply {
                    StandPart.entries.firstOrNull { it.name == saved[1] }?.let { part ->
                        selectPart(part)
                        saved[2].toIntOrNull()?.let(::selectBinding)
                    }
                }
            },
        )
    }

    internal var bodyRightPx by mutableStateOf<Float?>(null)
    internal var controlAnchor by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    internal var cameraSettled by mutableStateOf(false)
    private val assignmentClose = CloseRequest()
    internal var assignmentCloseToken by mutableStateOf<Long?>(null)
        private set
    fun requestAssignmentClose(onClosed: () -> Unit) {
        assignmentCloseToken = assignmentClose.request(onClosed)
    }
    fun cancelAssignmentClose() {
        assignmentClose.cancel()
        assignmentCloseToken = null
    }
    internal fun finishAssignmentClose(token: Long) {
        if (assignmentCloseToken != token) return
        assignmentCloseToken = null
        assignmentClose.complete(token)
    }
    private val closeRequest = CloseRequest()
    internal var closeToken by mutableStateOf<Long?>(null)
        private set

    fun requestClose(onClosed: () -> Unit) {
        closeToken = closeRequest.request(onClosed)
    }

    fun cancelClose() {
        cancelAssignmentClose()
        closeRequest.cancel()
        closeToken = null
    }

    internal fun finishClose(token: Long) {
        if (closeToken != token) return
        closeToken = null
        closeRequest.complete(token)
    }

    var snapshot by mutableStateOf(FloorSettingsSnapshot())
    var onAction: (String, Float?) -> Unit = { _, _ -> }
}
