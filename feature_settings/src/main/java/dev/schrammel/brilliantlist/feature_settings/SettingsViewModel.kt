package dev.schrammel.brilliantlist.feature_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings(
            rotationMode = RotationMode.RANDOM,
            rotationIntervalMinutes = 30,
            widgetAutoUpdate = true,
            notificationsEnabled = false,
            crisisRegion = "US",
            encryptionEnabled = true
        ))

    fun onRegionChange(region: String) {
        viewModelScope.launch { settingsRepository.setCrisisRegion(region) }
    }

    fun onWidgetIntervalChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setRotationIntervalMinutes(minutes) }
    }

    fun onRotationModeChange(mode: RotationMode) {
        viewModelScope.launch { settingsRepository.setRotationMode(mode) }
    }

    fun onAutoUpdateChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWidgetAutoUpdate(enabled) }
    }
}
