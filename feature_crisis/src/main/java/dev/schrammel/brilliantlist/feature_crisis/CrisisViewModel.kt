package dev.schrammel.brilliantlist.feature_crisis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.CrisisCatalog
import dev.schrammel.brilliantlist.core_model.CrisisResource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CrisisViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * The code of the configured country, for example "SE".
     *
     * It is empty when the country is not one the app knows.
     */
    val countryCode: StateFlow<String> = settingsRepository.settings
        .map { settings -> CrisisCatalog.normalize(settings.crisisRegion).orEmpty() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ""
        )

    /**
     * True when the configured country is not one of the supported ones. In
     * that case only the generic resources are shown and the screen says so
     * explicitly.
     */
    val countryUnknown: StateFlow<Boolean> = settingsRepository.settings
        .map { settings -> CrisisCatalog.normalize(settings.crisisRegion) == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Offline crisis resources for the configured country. */
    val resources: StateFlow<List<CrisisResource>> = settingsRepository.settings
        .map { settings ->
            val code = CrisisCatalog.normalize(settings.crisisRegion)
            if (code == null) {
                CrisisCatalog.fallbackResources
            } else {
                CrisisCatalog.resourcesFor(code)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CrisisCatalog.fallbackResources
        )
}
