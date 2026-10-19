package dev.schrammel.brilliantlist.feature_settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.schrammel.brilliantlist.core_model.RotationMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Crisis Region")
        TextField(
            value = settings.crisisRegion,
            onValueChange = viewModel::onRegionChange,
            label = { Text("Region") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Widget Refresh Interval (minutes)")
        Slider(
            value = settings.rotationIntervalMinutes.toFloat(),
            onValueChange = { viewModel.onWidgetIntervalChange(it.toInt()) },
            valueRange = 15f..120f,
            steps = 20
        )
        Text("${settings.rotationIntervalMinutes} min")
        Spacer(modifier = Modifier.height(16.dp))

        Text("Rotation Mode")
        Row {
            FilterChip(
                selected = settings.rotationMode == RotationMode.RANDOM,
                onClick = { viewModel.onRotationModeChange(RotationMode.RANDOM) },
                label = { Text("Random") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = settings.rotationMode == RotationMode.SEQUENTIAL,
                onClick = { viewModel.onRotationModeChange(RotationMode.SEQUENTIAL) },
                label = { Text("Sequential") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Widget auto update")
            Switch(
                checked = settings.widgetAutoUpdate,
                onCheckedChange = viewModel::onAutoUpdateChange
            )
        }
    }
}
