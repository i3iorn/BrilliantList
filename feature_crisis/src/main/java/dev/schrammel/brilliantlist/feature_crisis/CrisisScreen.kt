package dev.schrammel.brilliantlist.feature_crisis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import dev.schrammel.brilliantlist.core_model.CrisisResource

/**
 * Fully offline, region-aware crisis resources. All data is bundled; no network calls.
 * Dialing uses the platform dialer (a phone call is not a data connection).
 */
@Composable
fun CrisisScreen(viewModel: CrisisViewModel = hiltViewModel()) {
    val region by viewModel.region.collectAsState()
    val resources = viewModel.resourcesFor(region)
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Crisis Resources", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Region: $region", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(resources) { resource ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${resource.phone}"))
                        )
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(resource.name, style = MaterialTheme.typography.titleMedium)
                        Text(resource.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap to call ${resource.phone}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
