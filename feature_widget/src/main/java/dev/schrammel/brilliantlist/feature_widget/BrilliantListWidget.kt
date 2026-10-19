package dev.schrammel.brilliantlist.feature_widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Hilt entry point so the Glance receiver can reach the SingletonComponent graph
 * (Glance receivers can't use @AndroidEntryPoint).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun entryRepository(): EntryRepository
    fun settingsRepository(): SettingsRepository
}

class BrilliantListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Rotation engine: pick one entry (data must be fetched BEFORE provideContent,
        // whose lambda is not a coroutine scope).
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        ).entryRepository()
        val entry = repo.randomByType(EntryType.TEXT)
            ?: repo.observeEntries().first().firstOrNull { !it.isDeleted }

        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = entry?.textContent ?: "Add your first brilliant thing"
                )
            }
        }
    }
}

class BrilliantListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrilliantListWidget()

    /** (Re)schedules periodic rotation honoring the user's interval setting. */
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleRotation(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleRotation(context)
    }

    private fun scheduleRotation(context: Context) {
        val appContext = context.applicationContext
        val settingsRepository = EntryPointAccessors.fromApplication(
            appContext, WidgetEntryPoint::class.java
        ).settingsRepository()

        // Interval is read asynchronously; the worker also re-checks it each run.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            val minutes = settingsRepository.settings.first().rotationIntervalMinutes
                .coerceAtLeast(MIN_PERIOD_MINUTES)
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                WidgetRotationWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WidgetRotationWorker>(minutes.toLong(), TimeUnit.MINUTES)
                    .build()
            )
        }
    }

    private companion object {
        // WorkManager's minimum periodic interval is 15 minutes.
        const val MIN_PERIOD_MINUTES = 15
    }
}
