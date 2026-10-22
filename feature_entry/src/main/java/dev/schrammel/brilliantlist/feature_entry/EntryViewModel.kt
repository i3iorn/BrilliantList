package dev.schrammel.brilliantlist.feature_entry

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.schrammel.brilliantlist.core_data.media.MediaSink
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import dev.schrammel.brilliantlist.core_model.Settings
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One-shot events the screen shows as a snackbar. */
sealed interface EntryEvent {
    /** A plain message, e.g. "that image could not be added". */
    data class Message(val text: String) : EntryEvent

    /** An entry was deleted and can still be restored. */
    data class Deleted(val entry: Entry) : EntryEvent
}

/**
 * The state of the entry list.
 *
 * The list can be filtered by a search term and by the favorites, and it can
 * be switched to the trash to restore or to remove entries for good.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository,
    private val settingsRepository: SettingsRepository,
    val mediaLoader: MediaLoader
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Settings(
                rotationMode = dev.schrammel.brilliantlist.core_model.RotationMode.RANDOM,
                rotationIntervalMinutes = 30,
                widgetAutoUpdate = true,
                crisisRegion = dev.schrammel.brilliantlist.core_model.CrisisRegion.DEFAULT.code,
                sortMode = SortMode.MANUAL,
                widgetShowContent = false
            )
        )

    private val sortMode: StateFlow<SortMode> = settingsRepository.settings
        .map { it.sortMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortMode.MANUAL)

    /** The current search term, as the user typed it. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The search term the list is filtered with.
     *
     * It follows the query with a delay, so a search does not run a query
     * against the database for every keystroke.
     */
    private val appliedQuery: StateFlow<String> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * The entry list in the order the user chose. Re-subscribes when the sort
     * mode or the search term changes, so the list does not need to be
     * reloaded manually.
     */
    val entries: StateFlow<List<Entry>> =
        combine(sortMode, appliedQuery) { mode, search -> mode to search }
            .flatMapLatest { (mode, search) -> repository.observeEntries(mode, search) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The entries in the trash. */
    val deletedEntries: StateFlow<List<Entry>> = repository.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _favoritesOnly = MutableStateFlow(false)

    /** When true the list shows only favorited entries. */
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    /**
     * One-shot events: a deleted entry that can still be restored and error
     * messages. A SharedFlow (instead of state) so they are not shown again
     * on recomposition.
     */
    private val _events = MutableSharedFlow<EntryEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<EntryEvent> = _events.asSharedFlow()

    /** Adds a text entry. Kept as a single step to preserve the sub-2s capture goal. */
    fun addEntry(text: String?) {
        viewModelScope.launch { repository.addTextEntry(text) }
    }

    /** Captures an image from a picker Uri; encrypts the ContentResolver stream directly. */
    fun addImageEntry(uri: Uri) {
        viewModelScope.launch {
            // Opening the file of the content provider is a binder call, it
            // can take a moment and it must not block the screen.
            val stream = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            }
            if (stream == null) {
                _events.tryEmit(EntryEvent.Message(context.getString(R.string.entry_error_image)))
                return@launch
            }
            runCatching { repository.addImageEntry(stream) }
                .onSuccess { _events.tryEmit(EntryEvent.Message(context.getString(R.string.entry_added_image))) }
                .onFailure {
                    runCatching { stream.close() }
                    _events.tryEmit(EntryEvent.Message(context.getString(R.string.entry_error_image)))
                }
        }
    }

    /**
     * Opens an encrypted sink for a recording.
     *
     * The recorder writes into it, so the audio never exists as plaintext on
     * the disk.
     */
    suspend fun createAudioSink(): MediaSink? = runCatching {
        repository.openMediaSink(EntryType.AUDIO)
    }.getOrNull()

    /** Persists a finished recording. */
    fun completeAudioRecording(sink: MediaSink) {
        viewModelScope.launch {
            runCatching { repository.completeMediaSink(sink) }
                .onSuccess { _events.tryEmit(EntryEvent.Message(context.getString(R.string.entry_added_audio))) }
                .onFailure { _events.tryEmit(EntryEvent.Message(context.getString(R.string.entry_error_audio))) }
        }
    }

    /** Soft deletes an entry and offers it for undo. */
    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            repository.softDelete(entry.id)
            _events.tryEmit(EntryEvent.Deleted(entry))
        }
    }

    /** Restores a soft deleted entry. */
    fun undoDelete(entry: Entry) {
        viewModelScope.launch { repository.restore(entry) }
    }

    /** Removes an entry and its media for good. */
    fun deleteForever(entry: Entry) {
        viewModelScope.launch { repository.deletePermanently(entry) }
    }

    /** Empties the trash: the deleted entries and their media are removed. */
    fun emptyTrash() {
        viewModelScope.launch { repository.purgeDeleted(0) }
    }

    /** Toggles the favorite flag of an entry. */
    fun toggleFavorite(entry: Entry) {
        viewModelScope.launch { repository.setFavorite(entry.id, !entry.isFavorite) }
    }

    /**
     * Updates the text of an entry. For image and audio entries this is the
     * caption, for text entries the content.
     */
    fun updateText(entry: Entry, text: String?) {
        viewModelScope.launch { repository.updateText(entry.id, text?.trim()?.takeIf { it.isNotEmpty() }) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _favoritesOnly.value = enabled
    }

    /** Filters the list by a search term. */
    fun setQuery(query: String) {
        _query.value = query
    }

    /**
     * Copies the text to the clipboard and clears it again after a minute.
     *
     * The clipboard of the system is shared with every app on the device, so
     * the text of an entry should not sit there for hours. The clipboard is
     * only cleared if it still holds this text - a copy of something else is
     * not ours to clear.
     */
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(CLIPBOARD_LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                // The clipboard preview is hidden, so the text does not show
                // up on the screen of the person.
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_DELAY)
            val current = runCatching {
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            }.getOrNull()
            if (current == text) {
                clipboard.clearPrimaryClip()
            }
        }
    }

    /**
     * Moves an entry within the list. Indices refer to the active (non deleted)
     * entries; the repository rewrites the positions of exactly that list.
     */
    private companion object {
        /** The delay of the search, in milliseconds. */
        const val SEARCH_DEBOUNCE_MILLIS = 250L

        /** The label of the clipboard entry, so it can be recognised. */
        const val CLIPBOARD_LABEL = "BrilliantList"

        /** How long the text stays on the clipboard, in milliseconds. */
        const val CLIPBOARD_CLEAR_DELAY = 60_000L
    }
    fun moveEntry(from: Int, to: Int) {
        viewModelScope.launch {
            val current = entries.value.filterNot { it.isDeleted }.toMutableList()
            if (from !in current.indices || to !in current.indices || from == to) return@launch
            val entry = current.removeAt(from)
            current.add(to, entry)
            repository.reorder(current.map { it.id })
        }
    }
}
