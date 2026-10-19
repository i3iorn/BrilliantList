package dev.schrammel.brilliantlist.feature_entry

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_ui.components.EntryCard
import kotlin.math.roundToInt

@Composable
fun EntryScreen(viewModel: EntryViewModel = viewModel()) {
    val entries = viewModel.entries.collectAsState().value
    val listState = rememberLazyListState()
    var showAddDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }

    // Local visual order during an active drag; null means no drag in progress.
    var draggedIds by remember { mutableStateOf<List<java.util.UUID>?>(null) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    val activeIds = entries.filterNot { it.isDeleted }
    val displayList: List<Entry> = draggedIds?.mapNotNull { id -> activeIds.firstOrNull { it.id == id } }
        ?: activeIds

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Entry")
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
