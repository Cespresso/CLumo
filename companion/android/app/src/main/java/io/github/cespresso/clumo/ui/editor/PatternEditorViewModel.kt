package io.github.cespresso.clumo.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.data.DeviceRepository
import io.github.cespresso.clumo.data.PatternRepository
import io.github.cespresso.clumo.data.session.DeviceSessionRegistry
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.resolveAppearance
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun editorPattern(
    existing: Pattern?,
    name: String,
    cells: Long,
    newId: String,
): Pattern = Pattern(
    id = existing?.id ?: newId,
    name = name,
    bits = FaceBits.toBitsString(cells),
)

data class PatternEditorUiState(
    val patterns: List<Pattern> = emptyList(),
    val existing: Pattern? = null,
    val cells: Long = FaceBits.EMPTY,
    val initialized: Boolean = false,
    val appearance: DeviceAppearance = DeviceAppearance.DEFAULT,
    val stableDeviceId: String? = null,
    val livePreview: Boolean = false,
    val updating: Boolean = false,
    val operationFailed: Boolean = false,
)

sealed interface PatternEditorEvent {
    data object Close : PatternEditorEvent
}

private data class EditorLocalState(
    val cells: Long = FaceBits.EMPTY,
    val initialized: Boolean,
    val livePreview: Boolean = false,
    val updating: Boolean = false,
    val operationFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PatternEditorViewModel(
    private val address: String?,
    private val patternId: String?,
    private val registry: DeviceSessionRegistry,
    private val repository: DeviceRepository,
    preferences: AppPreferences,
    private val patternRepository: PatternRepository,
) : ViewModel() {
    private val local = MutableStateFlow(EditorLocalState(initialized = patternId == null))
    private val _events = MutableSharedFlow<PatternEditorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private var retryAction: (suspend () -> Unit)? = null

    private val stableDeviceId = if (address == null) {
        flowOf(null)
    } else {
        registry.sessions.map { it[address] }.flatMapLatest { session ->
            session?.deviceId ?: flowOf(null)
        }.map { it ?: repository.getByAddress(address)?.id }
    }

    val uiState = combine(
        patternRepository.patterns,
        preferences.deviceAppearances,
        stableDeviceId,
        local,
    ) { patterns, appearances, stableId, local ->
        PatternEditorUiState(
            patterns = patterns,
            existing = patternId?.let { id -> patterns.firstOrNull { it.id == id } },
            cells = local.cells,
            initialized = local.initialized,
            appearance = resolveAppearance(stableId, appearances),
            stableDeviceId = stableId,
            livePreview = local.livePreview,
            updating = local.updating,
            operationFailed = local.operationFailed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PatternEditorUiState(initialized = patternId == null),
    )

    init {
        viewModelScope.launch {
            patternRepository.patterns.collect { patterns ->
                val current = local.value
                if (current.initialized) return@collect
                val existing = patternId?.let { id -> patterns.firstOrNull { it.id == id } }
                    ?: return@collect
                local.value = current.copy(
                    cells = FaceBits.fromBitsString(existing.bits),
                    initialized = true,
                )
            }
        }
    }

    fun onCellsChanged(cells: Long) {
        local.update { it.copy(cells = cells) }
        if (local.value.livePreview) {
            address?.let(registry::get)?.previewFrame(FaceBits.toBitsString(cells))
        }
    }

    fun onLivePreviewChanged(enabled: Boolean) {
        local.update { it.copy(livePreview = enabled) }
        val session = address?.let(registry::get) ?: return
        if (enabled) {
            session.previewFrame(FaceBits.toBitsString(local.value.cells))
        } else {
            session.cancelPreview()
        }
    }

    fun save(rawName: String, fallbackPrefix: String) {
        val state = uiState.value
        val name = rawName.trim().ifEmpty { fallbackPrefix + (state.patterns.size + 1) }
        val pattern = editorPattern(
            existing = state.existing,
            name = name,
            cells = state.cells,
            newId = UUID.randomUUID().toString(),
        )
        runPersistence {
            val targetId = state.stableDeviceId ?: address
            if (targetId == null) {
                patternRepository.upsert(pattern)
            } else {
                patternRepository.saveAndApply(targetId, pattern)
                address?.let(registry::get)?.commitPattern(pattern)
            }
        }
    }

    fun delete() {
        val existing = uiState.value.existing ?: return
        runPersistence { patternRepository.remove(existing.id) }
    }

    /** The keep-alive would otherwise stream an abandoned edit for the session's life. */
    override fun onCleared() {
        address?.let(registry::get)?.cancelPreview()
    }

    fun retry() {
        retryAction?.let(::runPersistence)
    }

    fun dismissFailure() {
        retryAction = null
        local.update { it.copy(operationFailed = false) }
    }

    private fun runPersistence(operation: suspend () -> Unit) {
        if (local.value.updating) return
        retryAction = operation
        local.update { it.copy(updating = true, operationFailed = false) }
        viewModelScope.launch {
            runPatternEditorOperation(
                persist = operation,
                onSuccess = {
                    retryAction = null
                    local.update { it.copy(updating = false) }
                    _events.tryEmit(PatternEditorEvent.Close)
                },
                onFailure = {
                    local.update { it.copy(updating = false, operationFailed = true) }
                },
            )
        }
    }
}
