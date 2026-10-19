package dev.schrammel.brilliantlist.feature_crisis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CrisisScreen(region: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Crisis Resources for $region", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        // TODO: Show region-aware offline resources
        Text("If you need help, here are some resources available in your region.")
    }
}

