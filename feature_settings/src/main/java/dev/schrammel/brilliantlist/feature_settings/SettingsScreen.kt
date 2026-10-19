package dev.schrammel.brilliantlist.feature_settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentRegion: String,
    onRegionChange: (String) -> Unit,
    widgetInterval: Int,
    onWidgetIntervalChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Crisis Region")
        TextField(
            value = currentRegion,
            onValueChange = onRegionChange,
            label = { Text("Region") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Widget Refresh Interval (minutes)")
        Slider(
            value = widgetInterval.toFloat(),
            onValueChange = { onWidgetIntervalChange(it.toInt()) },
            valueRange = 5f..120f,
            steps = 23
        )
        Text("${widgetInterval} min")
    }
}

