package dev.schrammel.brilliantlist.feature_crisis

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.schrammel.brilliantlist.core_model.CrisisContact
import dev.schrammel.brilliantlist.core_model.CrisisResource

/**
 * Fully offline, region-aware crisis resources. All data is bundled; no network calls.
 * Dialing uses the platform dialer and texting uses the platform messaging app
 * (a phone call or a message is not a data connection).
 */
@Composable
fun CrisisScreen(viewModel: CrisisViewModel = hiltViewModel()) {
    val region by viewModel.region.collectAsState()
    val regionUnrecognized by viewModel.regionUnrecognized.collectAsState()
    val resources by viewModel.resources.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(
                R.string.crisis_region,
                region?.displayName ?: stringResource(R.string.crisis_region_unknown)
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        if (regionUnrecognized) {
            // Never silently show the generic list: the user has to know that
            // these are not region specific resources.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.crisis_region_unrecognized),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(resources) { index, resource ->
                CrisisResourceCard(
                    resource = resource,
                    // The first resource is the primary one: it gets the
                    // prominent button, so the first action is obvious.
                    isPrimary = index == 0,
                    onCall = { contact(context, resource) }
                )
            }
        }
    }
}

/**
 * Opens the right app for the resource.
 *
 * A text line is not callable and a helpline is not necessarily a text line,
 * so the type decides what happens. A wrong action in a crisis is worse than
 * no action at all.
 */
private fun contact(context: android.content.Context, resource: CrisisResource) {
    when (resource.contact) {
        CrisisContact.CALL -> {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${resource.phone}"))
            runCatching { context.startActivity(intent) }
        }

        CrisisContact.TEXT -> {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${resource.phone}"))
                .putExtra("sms_body", resource.messageBody.orEmpty())
            runCatching { context.startActivity(intent) }
        }

        CrisisContact.INFO -> Unit
    }
}

@Composable
private fun CrisisResourceCard(
    resource: CrisisResource,
    isPrimary: Boolean,
    onCall: () -> Unit
) {
    val actionLabel = when (resource.contact) {
        CrisisContact.CALL -> stringResource(R.string.crisis_action_call, resource.phone)
        CrisisContact.TEXT -> stringResource(R.string.crisis_action_text, resource.phone)
        CrisisContact.INFO -> resource.instruction.orEmpty()
    }

    if (resource.contact == CrisisContact.INFO) {
        // Nothing to open: the card is information, not a button.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = resource.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = resource.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!actionLabel.isEmpty()) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        return
    }

    // The card is not clickable itself: the button is the single, clearly
    // labelled target, so a screen reader announces exactly one action.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isPrimary) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = resource.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = resource.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isPrimary) {
                    Button(onClick = onCall) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onCall) { Text(actionLabel) }
                }
            }
        }
    }
}
