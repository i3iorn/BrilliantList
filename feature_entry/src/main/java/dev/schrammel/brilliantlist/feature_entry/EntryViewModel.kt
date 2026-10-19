package dev.schrammel.brilliantlist.feature_entry

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_model.Entry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EntryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository
) : ViewModel() {

    val entries: StateFlow<List<Entry>> = repository.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Adds a text entry. Kept as a single step to preserve the sub-2s capture goal. */
    fun addEntry(text: String?) {
        viewModelScope.launch { repository.addTextEntry(text) }
    }

    /** Captures an image from a picker Uri; encrypts the ContentResolver stream directly. */
    fun addImageEntry(uri: Uri) {
        viewModelScope.launch {
            val stream = context.contentResolver.openInputStream(uri) ?: return@launch
            runCatching { repository.addImageEntry(stream) }
                .onFailure { runCatching { stream.close() } }
        }
    }

    /** Persists an encrypted audio entry from the finished recording temp file. */
    fun addAudioEntry(tempFile: java.io.File) {
        viewModelScope.launch { runCatching { repository.addAudioEntry(tempFile) } }
    }

    fun deleteEntry(id: UUID) {
        viewModelScope.launch { repository.softDelete(id) }
    }

    fun reorderEntries(from: Int, to: Int) {
        viewModelScope.launch {
            val current = entries.value.filterNot { it.isDeleted }.toMutableList()
            if (from !in current.indices || to !in current.indices) return@launch
            val entry = current.removeAt(from)
            current.add(to, entry)
            repository.reorder(current.map { it.id })
        }
    }
}
