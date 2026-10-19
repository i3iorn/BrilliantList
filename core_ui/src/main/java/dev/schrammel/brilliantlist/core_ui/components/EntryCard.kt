package dev.schrammel.brilliantlist.core_ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.core_model.Entry

@Composable
fun EntryCard(
    entry: Entry,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(text = entry.textContent ?: "(No text)")
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}
