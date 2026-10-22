package dev.schrammel.brilliantlist.feature_entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Single step text capture, reused for editing an existing entry.
 *
 * Saving is disabled for blank input so no empty entries end up in the list,
 * the IME "Done" action saves, and the field is focused right away so the
 * keyboard is ready. The field has a label (not only a placeholder) so screen
 * readers announce what the text is for.
 *
 * The text can be copied to the clipboard, which is marked as sensitive so it
 * does not show up in the clipboard preview.
 */
@Composable
fun EntryTextDialog(
    title: String,
    initialText: String = "",
    confirmLabel: String = stringResource(R.string.action_save),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onCopy: ((String) -> Unit)? = null
) {
    var text by remember { mutableStateOf(initialText) }
    val trimmed = text.trim()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.entry_dialog_label)) },
                placeholder = { Text(stringResource(R.string.entry_dialog_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onCopy != null && trimmed.isNotEmpty()) {
                    TextButton(onClick = { onCopy(trimmed) }) {
                        Text(stringResource(R.string.action_copy))
                    }
                }
                TextButton(
                    onClick = { onConfirm(trimmed) },
                    enabled = trimmed.isNotEmpty()
                ) {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
