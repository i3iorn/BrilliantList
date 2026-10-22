package dev.schrammel.brilliantlist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import kotlinx.coroutines.delay

/**
 * Tracks whether the encrypted database can be opened right now.
 *
 * The master key requires an unlocked device, so the app has to wait for the
 * device to be unlocked after a reboot. The state is re-checked when the app
 * comes back to the foreground and polled while the device is locked.
 */
@Composable
fun rememberDeviceUnlocked(keyManager: DatabaseKeyManager): MutableState<Boolean> {
    val unlocked = remember { mutableStateOf(keyManager.isAvailable()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                unlocked.value = keyManager.isAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(unlocked.value) {
        while (!unlocked.value) {
            delay(POLL_INTERVAL_MS)
            unlocked.value = keyManager.isAvailable()
        }
    }

    return unlocked
}

private const val POLL_INTERVAL_MS = 1_000L
