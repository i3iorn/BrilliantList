package dev.schrammel.brilliantlist.feature_crisis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * Fully offline, region-aware crisis resources. All data is bundled; no
 * network calls. Dialing uses the platform dialer and texting uses the
 * platform messaging app (a phone call or a message is not a data
 * connection).
 */
@Composable
fun CrisisScreen(viewModel: CrisisViewModel = hiltViewModel()) {
    val countryCode by viewModel.countryCode.collectAsState()
    val countryUnknown by viewModel.countryUnknown.collectAsState()
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
                countryName(countryCode)
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        if (countryUnknown) {
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
            items(resources) { resource ->
                CrisisResourceCard(
                    resource = resource,
                    // The emergency number is the one that always works, so it
                    // is the prominent one.
                    isPrimary = resource.isEmergency,
                    onCall = { contact(context, resource) }
                )
            }
        }
    }
}

/** The name of the country, in the language of the country when it has one. */
private fun countryName(code: String): String {
    if (code.isEmpty()) return ""
    val country = dev.schrammel.brilliantlist.core_model.CrisisCatalog.country(code) ?: return code
    return country.localName
}

/**
 * Opens the right app for the resource.
 *
 * A text line is not callable and a helpline is not necessarily a text line,
 * so the type decides what happens. A wrong action in a crisis is worse than
 * no action at all.
 */
private fun contact(context: Context, resource: CrisisResource) {
    when (resource.contact) {
        CrisisContact.CALL -> {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${resource.phone}"))
            runCatching { context.startActivity(intent) }
        }

        CrisisContact.TEXT -> {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${resource.phone}"))
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
                if (resource.website.isNotEmpty()) {
                    Text(
                        text = resource.website,
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
            // The name in the language of the country, so a person who does
            // not speak English can recognise the line.
            if (!resource.localName.isNullOrBlank() && resource.localName != resource.name) {
                Text(
                    text = resource.localName!!,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = resource.name,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = resource.name,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (resource.description.isNotEmpty()) {
                Text(
                    text = resource.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val facts = listOfNotNull(
                resource.hours.takeIf { it.isNotEmpty() },
                resource.languages.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { languageName(it) },
                if (resource.free) stringResource(R.string.crisis_free) else null,
                if (resource.anonymous) stringResource(R.string.crisis_anonymous) else null
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isPrimary) {
                    Button(onClick = onCall) {
                        Text(actionLabel(resource))
                    }
                } else {
                    androidx.compose.material3.OutlinedButton(onClick = onCall) {
                        Text(actionLabel(resource))
                    }
                }
            }
            if (resource.verifiedAt.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.crisis_verified, resource.verifiedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The label of the button: what it does. */
@Composable
private fun actionLabel(resource: CrisisResource): String = when (resource.contact) {
    CrisisContact.CALL -> stringResource(R.string.crisis_action_call, resource.phone)
    CrisisContact.TEXT -> stringResource(R.string.crisis_action_text, resource.phone)
    CrisisContact.INFO -> resource.name
}

/** The name of a language, in that language. */
private fun languageName(code: String): String = LANGUAGE_NAMES[code] ?: code

/** The names of the languages the lines answer in. */
private val LANGUAGE_NAMES = mapOf(
    "en" to "English",
    "sv" to "Svenska",
    "de" to "Deutsch",
    "fr" to "Français",
    "es" to "Español",
    "pt" to "Português",
    "ar" to "العربية",
    "zh" to "中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "ru" to "Русский",
    "uk" to "Українська",
    "hi" to "हिन्दी",
    "bn" to "বাংলা",
    "tr" to "Türkçe",
    "vi" to "Tiếng Việt",
    "id" to "Bahasa Indonesia",
    "tl" to "Tagalog",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "th" to "ไทย",
    "fa" to "فارسی",
    "ur" to "اردو",
    "sw" to "Kiswahili",
    "fil" to "Filipino",
    "ne" to "नेपाली",
    "so" to "Soomaali",
    "he" to "עברית"
)
