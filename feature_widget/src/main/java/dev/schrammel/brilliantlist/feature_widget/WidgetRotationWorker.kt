package dev.schrammel.brilliantlist.feature_widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_data.repository.AppStateRepository
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.first

/**
 * Rotation engine: picks the next entry (random or sequential) and refreshes
 * every placed widget. Respects the user's widgetAutoUpdate setting.
 */
@HiltWorker
class WidgetRotationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val entryRepository: EntryRepository,
    private val settingsRepository: SettingsRepository,
    private val appStateRepository: AppStateRepository,
    private val keyManager: DatabaseKeyManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The encrypted database can only be opened while the device is
        // unlocked. Retry later instead of failing: the user may unlock the
        // device shortly.
        if (!keyManager.isAvailable()) return Result.retry()

        return try {
            val settings = settingsRepository.settings.first()
            if (!settings.widgetAutoUpdate) return Result.success()

            val entry = entryRepository.pickForRotation(
                type = EntryType.TEXT,
                mode = settings.rotationMode,
                lastShownId = appStateRepository.get(KEY_LAST_SHOWN_ENTRY),
                sortMode = settings.sortMode
            )
            entry?.let { appStateRepository.set(KEY_LAST_SHOWN_ENTRY, it.id.toString()) }

            BrilliantListWidget().updateAll(applicationContext)

            // A long interval is scheduled as a single delayed run, so it has
            // to schedule the next one itself.
            WidgetRotationScheduler.scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "widget_rotation"

        /** Key of the last shown entry, stored in the encrypted database. */
        const val KEY_LAST_SHOWN_ENTRY = "last_shown_entry_id"
    }
}
