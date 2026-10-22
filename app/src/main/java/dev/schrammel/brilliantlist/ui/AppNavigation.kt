package dev.schrammel.brilliantlist.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.schrammel.brilliantlist.AppViewModel
import dev.schrammel.brilliantlist.R
import dev.schrammel.brilliantlist.feature_crisis.CrisisScreen
import dev.schrammel.brilliantlist.feature_entry.EntryScreen
import dev.schrammel.brilliantlist.feature_settings.SettingsScreen
import dev.schrammel.brilliantlist.feature_widget.WidgetRotationScheduler

private const val ROUTE_ENTRIES = "entries"
private const val ROUTE_CRISIS = "crisis"
private const val ROUTE_SETTINGS = "settings"

/**
 * The app shell: top bar, bottom navigation and the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    // The worker only re-reads the interval setting, it does not change the
    // period of already scheduled work. Re-scheduling here makes a changed
    // interval take effect immediately.
    LaunchedEffect(settings.rotationIntervalMinutes) {
        WidgetRotationScheduler.schedule(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResFor(currentDestination?.route))) },
                actions = {
                    // The crisis page is a tab, but in a crisis it has to be
                    // reachable right away - so it is also in the top bar.
                    IconButton(
                        onClick = { navController.navigate(ROUTE_CRISIS) { launchSingleTop = true } }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = stringResource(R.string.nav_support)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination.isRoute(ROUTE_ENTRIES),
                    onClick = { navController.navigate(ROUTE_ENTRIES) { launchSingleTop = true } },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.nav_entries)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_entries)) }
                )
                NavigationBarItem(
                    selected = currentDestination.isRoute(ROUTE_CRISIS),
                    onClick = { navController.navigate(ROUTE_CRISIS) { launchSingleTop = true } },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = stringResource(R.string.nav_crisis)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_crisis)) }
                )
                NavigationBarItem(
                    selected = currentDestination.isRoute(ROUTE_SETTINGS),
                    onClick = { navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ENTRIES,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_ENTRIES) { EntryScreen() }
            composable(ROUTE_CRISIS) { CrisisScreen() }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
        }
    }
}

private fun androidx.navigation.NavDestination?.isRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

private fun titleResFor(route: String?): Int = when (route) {
    ROUTE_CRISIS -> R.string.nav_crisis
    ROUTE_SETTINGS -> R.string.nav_settings
    else -> R.string.nav_entries
}
