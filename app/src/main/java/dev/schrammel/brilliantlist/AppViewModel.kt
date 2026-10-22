package dev.schrammel.brilliantlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.CrisisCatalog
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.Settings
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * App level state: the settings the shell needs (currently the widget
 * interval, so a changed interval can re-schedule the rotation work).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val entryRepository: EntryRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Settings(
                rotationMode = RotationMode.RANDOM,
                rotationIntervalMinutes = 30,
                widgetAutoUpdate = true,
                crisisRegion = CrisisCatalog.DEFAULT_COUNTRY,
                sortMode = SortMode.MANUAL,
                widgetShowContent = false
            )
        )

    init {
        // A recording that was interrupted - the process was killed while the
        // microphone was on - leaves a file behind that no entry points to.
        // The sweep runs in the background, the app does not wait for it.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { entryRepository.sweepOrphanedMedia() }
            }
        }
    }
}
