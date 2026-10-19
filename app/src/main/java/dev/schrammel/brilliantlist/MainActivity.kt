package dev.schrammel.brilliantlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.schrammel.brilliantlist.core_ui.theme.BrilliantListTheme
import dev.schrammel.brilliantlist.feature_entry.EntryScreen
import dev.schrammel.brilliantlist.feature_crisis.CrisisScreen
import dev.schrammel.brilliantlist.feature_settings.SettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrilliantListTheme {
                AppNav()
            }
        }
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    Column(modifier = Modifier.fillMaxSize()) {
        // Simple navigation: without this the crisis/settings destinations are unreachable.
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { navController.navigate("entries") }) { Text("Entries") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { navController.navigate("crisis") }) { Text("Crisis") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { navController.navigate("settings") }) { Text("Settings") }
        }
        NavHost(navController, startDestination = "entries") {
            composable("entries") { EntryScreen() }
            composable("crisis") { CrisisScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}