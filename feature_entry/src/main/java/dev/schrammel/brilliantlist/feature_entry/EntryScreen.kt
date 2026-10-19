package dev.schrammel.brilliantlist.feature_entry

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_ui.components.EntryCard
import java.io.File
import kotlin.math.roundToInt

@Composable
fun EntryScreen(viewModel: EntryViewModel = viewModel()) {
    val entries = viewModel.entries.collectAsState().value
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }

    // Local visual order during an active drag; null means no drag in progress.
    var draggedIds by remember { mutableStateOf<List<java.util.UUID>?>(null) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::addImageEntry) }

    fun startRecording() {
        // Device hardware can fail (mic busy, codec error); guard against crash.
        val file = File.createTempFile("rec_", ".m4a", context.cacheDir)
        val rec = MediaRecorder(context)
        runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            tempAudioFile = file
            isRecording = true
        }.onFailure {
            runCatching { rec.release() }
            file.delete()
        }
    }

    fun stopRecording() {
        recorder?.let { rec ->
            val stopped = runCatching {
                rec.stop()
                rec.release()
            }.isSuccess
            // Only persist a recording that actually stopped cleanly; a failed stop
            // produces an unreadable file.
            if (stopped) {
                tempAudioFile?.let(viewModel::addAudioEntry)
            } else {
                tempAudioFile?.delete()
            }
        }
        recorder = null
        tempAudioFile = null
        isRecording = false
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    val activeIds = entries.filterNot { it.isDeleted }
    val displayList: List<Entry> = draggedIds?.mapNotNull { id -> activeIds.firstOrNull { it.id == id } }
        ?: activeIds

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) { Text("Add Entry") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) { Text("📷") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    if (isRecording) {
                        stopRecording()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) { Text(if (isRecording) "⏹" else "🎙") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(displayList, key = { _, entry -> entry.id }) { index, entry ->
                val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }
                EntryCard(
                    entry = entry,
                    onDelete = { viewModel.deleteEntry(entry.id) },
                    modifier = Modifier.pointerInput(entry.id, displayList.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                totalDragOffset = 0f
                                if (itemInfo != null) {
                                    draggedIds = displayList.map { it.id }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val current = draggedIds ?: return@detectDragGesturesAfterLongPress
                                val itemSize = (itemInfo?.size ?: 64).coerceAtLeast(1)
                                totalDragOffset += dragAmount.y
                                val from = current.indexOf(entry.id)
                                val stepsMoved = (totalDragOffset / itemSize).roundToInt()
                                val toIndex = from + stepsMoved
                                if (toIndex in current.indices && toIndex != from) {
                                    val updated = current.toMutableList()
                                    val moved = updated.removeAt(from)
                                    updated.add(toIndex, moved)
                                    draggedIds = updated
                                }
                            },
                            onDragEnd = {
                                draggedIds?.let { ids ->
                                    val from = activeIds.indexOfFirst { it.id == entry.id }
                                    val to = ids.indexOf(entry.id)
                                    if (from >= 0 && to >= 0 && from != to) {
                                        viewModel.reorderEntries(from, to)
                                    }
                                }
                                draggedIds = null
                                totalDragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIds = null
                                totalDragOffset = 0f
                            }
                        )
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Entry") },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    placeholder = { Text("Capture a brilliant thing…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addEntry(draftText.ifBlank { null })
                    draftText = ""
                    showAddDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
