package dev.schrammel.brilliantlist.feature_widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.widgetAutoUpdate) return Result.success()

        val entry = entryRepository.pickForRotation(
            type = EntryType.TEXT,
            mode = settings.rotationMode,
            lastShownId = settingsRepository.lastShownEntryId()
        )
        entry?.let { settingsRepository.setLastShownEntryId(it.id.toString()) }

        BrilliantListWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_rotation"
    }
}
