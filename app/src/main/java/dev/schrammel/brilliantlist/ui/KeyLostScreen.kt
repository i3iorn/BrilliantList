package dev.schrammel.brilliantlist.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.R
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import java.io.File

/**
 * Shown when the encryption key is gone.
 *
 * The key lives in the Keystore of the device. If it is lost - the device
 * was reset, the screen lock was removed in a way that invalidated the key -
 * the entries cannot be decrypted any more. The app has to say so and offer
 * a way out instead of crashing.
 */
@Composable
fun KeyLostScreen(onStartOver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.app_key_lost_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.app_key_lost_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onStartOver,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.app_key_lost_action))
        }
    }
}

/**
 * Removes everything that cannot be decrypted any more and starts the app
 * again.
 *
 * The database file is deleted (it cannot be opened without the key) and the
 * media files go with it. The app is then started again, so everything is
 * built from scratch.
 */
fun startOver(context: Context, keyManager: DatabaseKeyManager) {
    context.deleteDatabase(DATABASE_NAME)
    File(context.filesDir, MEDIA_DIRECTORY).listFiles()?.forEach { it.delete() }
    keyManager.wipe()

    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
}

/** The name of the database file, see [dev.schrammel.brilliantlist.core_data.database.AppDatabase]. */
private const val DATABASE_NAME = "brilliantlist.db"

/** The directory the media files live in. */
private const val MEDIA_DIRECTORY = "media"
