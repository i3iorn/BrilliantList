package dev.schrammel.brilliantlist.feature_settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.CrisisRegion
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.Settings
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One-shot events of the settings screen. */
sealed interface SettingsEvent {
    /** A message that is shown as a snackbar. */
    data class Message(val text: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
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
                crisisRegion = CrisisRegion.DEFAULT.code,
                sortMode = SortMode.MANUAL,
                widgetShowContent = false
            )
        )

    private val _events = MutableSharedFlow<SettingsEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun onRegionChange(region: CrisisRegion) {
        viewModelScope.launch { settingsRepository.setCrisisRegion(region.code) }
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

    fun onSortModeChange(mode: SortMode) {
        viewModelScope.launch { settingsRepository.setSortMode(mode) }
    }

    fun onWidgetShowContentChange(show: Boolean) {
        viewModelScope.launch { settingsRepository.setWidgetShowContent(show) }
    }

    fun onAppLockChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    /**
     * Writes an encrypted export of the journal to [uri].
     *
     * The export is encrypted with the passphrase the user chose, so it can
     * be restored on another device.
     */
    fun exportJournal(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        entryRepository.exportTo(output, passphrase)
                    } ?: error("the file could not be opened")
                }
            }
            passphrase.fill('\u0000')
            _events.tryEmit(
                SettingsEvent.Message(
                    context.getString(
                        if (message.isSuccess) {
                            R.string.settings_export_done
                        } else {
                            R.string.settings_export_failed
                        }
                    )
                )
            )
        }
    }

    /** Restores an encrypted export from [uri]. */
    fun importJournal(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        entryRepository.importFrom(input, passphrase)
                    } ?: error("the file could not be opened")
                }
            }
            passphrase.fill('\u0000')
            _events.tryEmit(
                SettingsEvent.Message(
                    if (count.isSuccess) {
                        context.getString(R.string.settings_import_done, count.getOrDefault(0))
                    } else {
                        context.getString(R.string.settings_import_failed)
                    }
                )
            )
        }
    }

    /** Deletes every entry, every media file and replaces the encryption key. */
    fun eraseEverything() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { entryRepository.eraseEverything() }
            }
            _events.tryEmit(
                SettingsEvent.Message(context.getString(R.string.settings_erase_done))
            )
        }
    }
}
