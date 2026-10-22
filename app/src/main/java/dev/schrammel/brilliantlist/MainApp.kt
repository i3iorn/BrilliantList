package dev.schrammel.brilliantlist

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.feature_entry.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MainApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var keyManager: DatabaseKeyManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // A recording is encrypted and deleted as soon as it stops. If the
        // process was killed while recording, the raw file is still in the
        // cache - remove it so no plaintext audio is left behind.
        AudioRecorder.purgeStaleRecordings(this)

        // The Keystore is slow the first time: the key has to be created.
        // Doing it in the background keeps the first frame of the app smooth.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            keyManager.warmUp()
        }
    }
}
