package dev.schrammel.brilliantlist.feature_crisis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.CrisisCatalog
import dev.schrammel.brilliantlist.core_model.CrisisRegion
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

    private val regionCode: StateFlow<String> = settingsRepository.settings
        .map { it.crisisRegion }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CrisisRegion.DEFAULT.code
        )

    /** The configured region, or null when it is not one of the supported ones. */
    val region: StateFlow<CrisisRegion?> = regionCode
        .map { CrisisCatalog.normalize(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CrisisRegion.DEFAULT
        )

    /**
     * True when the configured region is not recognized. In that case only the
     * generic resources are shown and the screen says so explicitly.
     */
    val regionUnrecognized: StateFlow<Boolean> = regionCode
        .map { CrisisCatalog.normalize(it) == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Offline crisis resources for the configured region. */
    val resources: StateFlow<List<CrisisResource>> = regionCode
        .map { CrisisCatalog.resourcesFor(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CrisisCatalog.resourcesFor(CrisisRegion.DEFAULT)
        )
}
