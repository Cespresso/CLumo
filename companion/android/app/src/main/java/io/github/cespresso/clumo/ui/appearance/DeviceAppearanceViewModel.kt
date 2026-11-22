package io.github.cespresso.clumo.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cespresso.clumo.data.AppearancePreferences
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceNaming
import io.github.cespresso.clumo.domain.RgbColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceSaveResult(
    val appearance: DeviceAppearance,
    val saveFailed: Boolean,
)

internal suspend fun saveAppearanceOptimistically(
    persisted: DeviceAppearance,
    next: DeviceAppearance,
    persist: suspend () -> Unit,
): AppearanceSaveResult =
    try {
        persist()
        AppearanceSaveResult(next, saveFailed = false)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AppearanceSaveResult(persisted, saveFailed = true)
    }

data class DeviceAppearanceUiState(
    val deviceName: String,
    val appearance: DeviceAppearance = DeviceAppearance.DEFAULT,
    val saveFailed: Boolean = false,
    val saving: Boolean = false,
    val kind: io.github.cespresso.clumo.domain.DeviceKind = io.github.cespresso.clumo.domain.DeviceKind.CLUMO,
)

class DeviceAppearanceViewModel(
    private val deviceId: String,
    private val fallbackName: String?,
    private val preferences: AppearancePreferences,
    kind: io.github.cespresso.clumo.domain.DeviceKind = io.github.cespresso.clumo.domain.DeviceKind.CLUMO,
    initialAppearance: DeviceAppearance = if (kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) defaultStandAppearance else DeviceAppearance.DEFAULT,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DeviceAppearanceUiState(
            deviceName = fallbackName ?: deviceId,
            kind = kind,
            appearance = initialAppearance,
        ),
    )
    val uiState: StateFlow<DeviceAppearanceUiState> = _uiState.asStateFlow()

    private val defaultAppearance = if (kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) defaultStandAppearance else DeviceAppearance.DEFAULT
    private var persistedAppearance = initialAppearance
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            combine(preferences.deviceAppearances, preferences.aliases, ::Pair).collect { (appearances, aliases) ->
                persistedAppearance = appearances[deviceId] ?: defaultAppearance
                _uiState.update { current ->
                    current.copy(
                        deviceName = DeviceNaming.displayName(
                            deviceId = deviceId,
                            aliases = aliases,
                            fallbackName = fallbackName,
                        ),
                        appearance = if (current.saving) current.appearance else persistedAppearance,
                        saveFailed = if (current.saving) current.saveFailed else false,
                    )
                }
            }
        }
    }

    fun onColorSelected(part: AppearancePart, color: RgbColor) {
        persist(_uiState.value.appearance.recolor(part, color), reset = false)
    }

    internal fun updateGroup(group: ColorGroup, color: RgbColor) = persist(_uiState.value.appearance.recolorGroup(group.name, color), reset = false)

    internal fun applyTheme(theme: StandTheme) = persist(theme.applyTo(_uiState.value.appearance), reset = false)

    internal fun assignGroup(part: AppearancePart, group: ColorGroup?) = persist(_uiState.value.appearance.assign(part, group), reset = false)

    fun reset() {
        persist(defaultAppearance, reset = true)
    }

    private fun persist(next: DeviceAppearance, reset: Boolean) {
        saveJob?.cancel()
        val rollback = persistedAppearance
        _uiState.update { it.copy(appearance = next, saveFailed = false, saving = true) }
        saveJob = viewModelScope.launch {
            val result = saveAppearanceOptimistically(
                persisted = rollback,
                next = next,
                persist = {
                    if (reset) {
                        preferences.resetDeviceAppearance(deviceId)
                    } else {
                        preferences.setDeviceAppearance(deviceId, next)
                    }
                },
            )
            _uiState.update {
                it.copy(
                    appearance = result.appearance,
                    saveFailed = result.saveFailed,
                    saving = false,
                )
            }
        }
    }
}
