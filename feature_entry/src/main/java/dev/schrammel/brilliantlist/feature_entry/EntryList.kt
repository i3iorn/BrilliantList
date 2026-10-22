package dev.schrammel.brilliantlist.feature_entry

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_ui.components.EntryCard
import dev.schrammel.brilliantlist.core_ui.components.formatEntryTime
import java.util.UUID

/**
 * The reorderable list of entries.
 *
 * Reordering is available by long press drag *and* through accessibility
 * actions ("Move up"/"Move down"), so it is not mouse/touch only.
 * Reordering is disabled while the favorites filter is active or when the list
 * is sorted by date: in both cases the visible list is not the list the
 * positions belong to.
 */
@Composable
fun EntryList(
    entries: List<Entry>,
    favoritesOnly: Boolean,
    manualOrder: Boolean,
    mediaLoader: MediaLoader,
    onDelete: (Entry) -> Unit,
    onToggleFavorite: ((Entry) -> Unit)?,
    onEdit: ((Entry) -> Unit)?,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    inTrash: Boolean = false,
    onRestore: ((Entry) -> Unit)? = null,
    emptyText: String? = null
) {
    // The trash shows the deleted entries, everywhere else they are hidden.
    val active = if (inTrash) entries else entries.filterNot { it.isDeleted }
    val visible = if (favoritesOnly) active.filter { it.isFavorite } else active

    if (visible.isEmpty()) {
        EmptyEntries(
            text = emptyText ?: stringResource(
                if (favoritesOnly) R.string.entry_empty_favorites else R.string.entry_empty
            ),
            modifier = modifier
        )
        return
    }

    val listState = rememberLazyListState()
    // Local visual order during an active drag; null means no drag in progress.
    var draggedIds by remember { mutableStateOf<List<UUID>?>(null) }
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    val reorderEnabled = !favoritesOnly && manualOrder && !inTrash

    val moveUpLabel = stringResource(R.string.entry_move_up)
    val moveDownLabel = stringResource(R.string.entry_move_down)
    val positionTemplate = stringResource(R.string.entry_position)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(visible, key = { _, entry -> entry.id }) { index, entry ->
            val itemInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == entry.id }

            val dragModifier = if (reorderEnabled) {
                Modifier.pointerInput(entry.id, visible.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            totalDragOffset = 0f
                            draggedIds = visible.map { it.id }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedIds ?: return@detectDragGesturesAfterLongPress
                            val itemSize = (itemInfo?.size ?: DEFAULT_ITEM_SIZE_PX).coerceAtLeast(1)
                            totalDragOffset += dragAmount.y
                            val from = current.indexOf(entry.id)
                            val to = dragTargetIndex(
                                from = from,
                                dragOffsetPx = totalDragOffset,
                                itemSizePx = itemSize,
                                itemCount = current.size
                            )
                            if (to != from && to in current.indices) {
                                val updated = current.toMutableList()
                                val moved = updated.removeAt(from)
                                updated.add(to, moved)
                                draggedIds = updated
                            }
                        },
                        onDragEnd = {
                            draggedIds?.let { ids ->
                                val from = active.indexOfFirst { it.id == entry.id }
                                val to = ids.indexOf(entry.id)
                                if (from >= 0 && to >= 0 && from != to) onMove(from, to)
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
            } else {
                Modifier
            }

            val accessibilityModifier = if (reorderEnabled) {
                Modifier.semantics {
                    customActions = listOfNotNull(
                        if (index > 0) {
                            CustomAccessibilityAction(moveUpLabel) {
                                onMove(index, index - 1)
                                true
                            }
                        } else {
                            null
                        },
                        if (index < visible.lastIndex) {
                            CustomAccessibilityAction(moveDownLabel) {
                                onMove(index, index + 1)
                                true
                            }
                        } else {
                            null
                        }
                    )
                }
            } else {
                Modifier
            }

            EntryCard(
                entry = entry,
                modifier = dragModifier.then(accessibilityModifier),
                onClick = if (inTrash) null else onEdit?.let { edit -> { edit(entry) } },
                onDelete = { onDelete(entry) },
                onRestore = onRestore?.let { restore -> { restore(entry) } },
                onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(entry) } },
                mediaContent = { EntryMedia(entry, mediaLoader) },
                positionDescription = String.format(positionTemplate, index + 1, visible.size),
                timestamp = formatEntryTime(entry.createdAt)
            )
        }
    }
}

@Composable
private fun EmptyEntries(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Fallback item height used until the item has been measured. */
private const val DEFAULT_ITEM_SIZE_PX = 64
