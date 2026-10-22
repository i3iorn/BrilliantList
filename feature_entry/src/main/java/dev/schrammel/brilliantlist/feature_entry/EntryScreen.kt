package dev.schrammel.brilliantlist.feature_entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.schrammel.brilliantlist.core_data.media.MediaSink
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.launch

/**
 * Entry list: the main capture screen.
 *
 * Capture stays a single step (text, photo, audio) and every action is
 * reachable with a screen reader: the photo/microphone buttons carry content
 * descriptions instead of emoji, and the list can be reordered with
 * accessibility actions.
 */
@Composable
fun EntryScreen(viewModel: EntryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val deletedEntries by viewModel.deletedEntries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val query by viewModel.query.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var captureText by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    val recorder = remember { AudioRecorder(context) }
    var pendingSink by remember { mutableStateOf<MediaSink?>(null) }

    val copiedMessage = stringResource(R.string.entry_copied)
    val audioErrorMessage = stringResource(R.string.entry_error_audio)

    fun saveCapture() {
        val text = captureText.trim()
        if (text.isEmpty()) return
        viewModel.addEntry(text)
        captureText = ""
        focusManager.clearFocus()
    }

    // The recording writes into the encrypted media file, so the audio never
    // exists as plaintext on the disk.
    fun startRecording() {
        scope.launch {
            val sink = viewModel.createAudioSink()
            if (sink == null) {
                snackbarHostState.showSnackbar(audioErrorMessage)
                return@launch
            }
            if (recorder.start(sink.output)) {
                pendingSink = sink
            } else {
                sink.abort()
                snackbarHostState.showSnackbar(audioErrorMessage)
            }
        }
    }

    fun stopRecording() {
        recorder.stop()
        pendingSink?.let { sink ->
            pendingSink = null
            viewModel.completeAudioRecording(sink)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::addImageEntry) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    // The microphone must not stay on while the app is in the background: the
    // recording would keep running (and the file would keep growing) after
    // the user left the app. The note is kept, it is just cut off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                stopRecording()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // The screen is left: the recording is finished, not thrown away.
            stopRecording()
            recorder.dispose()
        }
    }

    // The recording reached the maximum length: it is saved and the user is
    // told that it was cut off.
    LaunchedEffect(recorder.limitReached) {
        if (recorder.limitReached) {
            stopRecording()
            snackbarHostState.showSnackbar(context.getString(R.string.entry_recording_limit))
        }
    }

    // One-shot events: errors are plain messages, a deletion offers an undo.
    // The undo snackbar uses the long duration so it can be reached with a
    // screen reader.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EntryEvent.Message -> snackbarHostState.showSnackbar(
                    message = event.text,
                    duration = SnackbarDuration.Short
                )

                is EntryEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.entry_deleted_message),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.entry)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!showTrash) {
                // The capture field: one step, no dialog.
                OutlinedTextField(
                    value = captureText,
                    onValueChange = { captureText = it },
                    label = { Text(stringResource(R.string.entry_capture_label)) },
                    placeholder = { Text(stringResource(R.string.entry_capture_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveCapture() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.entry_search)) },
                    placeholder = { Text(stringResource(R.string.entry_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.entry_search_clear)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EntryToolbar(
                isRecording = recorder.isRecording,
                recordingSeconds = recorder.elapsedSeconds,
                favoritesOnly = favoritesOnly,
                showSearch = showSearch,
                showTrash = showTrash,
                inTrash = showTrash,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onToggleRecording = {
                    if (recorder.isRecording) {
                        stopRecording()
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onToggleFavorites = { viewModel.setFavoritesOnly(!favoritesOnly) },
                onToggleSearch = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.setQuery("")
                },
                onToggleTrash = {
                    showTrash = !showTrash
                    if (showTrash) {
                        showSearch = false
                        viewModel.setQuery("")
                    }
                }
            )

            if (showTrash) {
                EntryList(
                    entries = deletedEntries,
                    favoritesOnly = false,
                    manualOrder = false,
                    inTrash = true,
                    mediaLoader = viewModel.mediaLoader,
                    onDelete = viewModel::deleteForever,
                    onRestore = viewModel::undoDelete,
                    onToggleFavorite = null,
                    onEdit = null,
                    onMove = { _, _ -> },
                    emptyText = stringResource(R.string.entry_empty_trash)
                )
            } else {
                EntryList(
                    entries = entries,
                    favoritesOnly = favoritesOnly,
                    manualOrder = settings.sortMode == SortMode.MANUAL,
                    inTrash = false,
                    mediaLoader = viewModel.mediaLoader,
                    onDelete = viewModel::deleteEntry,
                    onRestore = null,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEdit = { editingEntry = it },
                    onMove = viewModel::moveEntry,
                    emptyText = if (query.isNotEmpty()) {
                        stringResource(R.string.entry_empty_search)
                    } else if (favoritesOnly) {
                        stringResource(R.string.entry_empty_favorites)
                    } else {
                        stringResource(R.string.entry_empty)
                    }
                )
            }
        }
    }

    editingEntry?.let { entry ->
        EntryTextDialog(
            title = stringResource(R.string.entry_edit_title),
            initialText = entry.textContent.orEmpty(),
            onDismiss = { editingEntry = null },
            onConfirm = { text ->
                viewModel.updateText(entry, text)
                editingEntry = null
            },
            onCopy = { text ->
                copyToClipboard(context, text)
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
            }
        )
    }
}

/**
 * Copies text to the clipboard.
 *
 * The clipboard is shared with every app on the device, so the entry is
 * marked as sensitive: the clipboard preview is hidden on Android 13 and
 * later.
 */
private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    val clip = android.content.ClipData.newPlainText("BrilliantList", text)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

/**
 * The capture toolbar.
 *
 * Internal (not private) so the accessibility tests can render it on its own,
 * e.g. with a scaled font.
 */
@Composable
internal fun EntryToolbar(
    isRecording: Boolean,
    recordingSeconds: Int = 0,
    favoritesOnly: Boolean,
    showSearch: Boolean = false,
    showTrash: Boolean = false,
    inTrash: Boolean = false,
    onPickImage: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleSearch: () -> Unit = {},
    onToggleTrash: () -> Unit = {}
) {
    // The trash is about restoring and deleting, so the capture actions are
    // hidden there: a new entry would not show up in the list the user is
    // looking at.
    if (inTrash) {
        IconButton(
            onClick = onToggleTrash,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_show_entries)
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPickImage,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_photo),
                contentDescription = stringResource(R.string.entry_add_image)
            )
        }

        val recordingLabel = if (isRecording) {
            stringResource(R.string.entry_recording, formatSeconds(recordingSeconds))
        } else {
            null
        }

        IconButton(
            onClick = onToggleRecording,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic
                ),
                contentDescription = stringResource(
                    if (isRecording) R.string.entry_stop_recording else R.string.entry_record
                )
            )
        }

        if (recordingLabel != null) {
            // The recording has to be visible, otherwise the user does not
            // know that the microphone is on. The label is the announcement,
            // the button says that it stops the recording.
            Text(
                text = recordingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // The toggles carry their state, so a screen reader says whether they
        // are on. The label stays the same, so it is still understandable
        // without seeing the screen.
        IconToggleButton(
            checked = favoritesOnly,
            onCheckedChange = { onToggleFavorites() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (favoritesOnly) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                contentDescription = stringResource(R.string.entry_favorites)
            )
        }

        IconToggleButton(
            checked = showSearch,
            onCheckedChange = { onToggleSearch() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.entry_search)
            )
        }

        IconToggleButton(
            checked = showTrash,
            onCheckedChange = { onToggleTrash() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_show_trash)
            )
        }
    }
}

/** Formats the recording time as minutes:seconds. */
internal fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}
