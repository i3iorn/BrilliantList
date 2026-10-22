package dev.schrammel.brilliantlist.core_ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import dev.schrammel.brilliantlist.core_ui.R

/**
 * A single entry in the list.
 *
 * Every actionable element carries a content description and a label that
 * makes sense out of context for screen reader users, and the text fallback
 * names the entry type so image/audio entries are not announced as "(No text)".
 *
 * A deleted entry shows the actions that apply to it: it can be restored or
 * removed for good.
 */
@Composable
fun EntryCard(
    entry: Entry,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    mediaContent: (@Composable () -> Unit)? = null,
    positionDescription: String? = null,
    timestamp: String? = null
) {
    // The position is announced by screen readers when the list is reordered.
    val semanticsModifier = if (positionDescription != null) {
        modifier.semantics { stateDescription = positionDescription }
    } else {
        modifier
    }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                mediaContent?.invoke()
                // The media slot is the content of an image/audio entry; the
                // type label is only needed when there is nothing to show.
                val label = entry.textContent
                if (label != null || mediaContent == null) {
                    Text(
                        text = label ?: stringResource(entry.type.labelRes()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onRestore != null) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.entry_restore))
                }
            }

            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (entry.isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Filled.FavoriteBorder
                        },
                        contentDescription = stringResource(
                            if (entry.isFavorite) {
                                R.string.entry_unfavorite
                            } else {
                                R.string.entry_favorite
                            }
                        )
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            // In the trash the delete is final, so it says so.
                            if (entry.isDeleted) {
                                R.string.entry_delete_forever
                            } else {
                                R.string.entry_delete
                            }
                        )
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = semanticsModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { content() }
    } else {
        Card(
            modifier = semanticsModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { content() }
    }
}

/** Human readable fallback label for entries without text content. */
private fun EntryType.labelRes(): Int = when (this) {
    EntryType.TEXT -> R.string.entry_type_text
    EntryType.IMAGE -> R.string.entry_type_image
    EntryType.AUDIO -> R.string.entry_type_audio
}
