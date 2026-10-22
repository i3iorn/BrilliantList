package dev.schrammel.brilliantlist.feature_widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic widget rotation.
 *
 * This is public so the app can re-schedule the work when the user changes
 * the interval in settings. The worker only re-reads the setting, it does not
 * change the period of already scheduled work, so without an explicit
 * re-schedule a new interval would never take effect.
 *
 * WorkManager's minimum periodic interval is 15 minutes. A longer interval
 * (a day, for example) is scheduled as a one time request with a delay: it
 * runs once and schedules the next run itself.
 */
object WidgetRotationScheduler {

    /** WorkManager's minimum periodic interval is 15 minutes. */
    const val MIN_PERIOD_MINUTES = 15

    /** The name of the periodic work. */
    const val WORK_NAME_PERIODIC = "widget_rotation"

    /** The name of the single delayed run that schedules the next one. */
    const val WORK_NAME_NEXT = "widget_rotation_next"

    /** Enqueues (or updates) the rotation, honoring the interval setting. */
    suspend fun schedule(context: Context) {
        val appContext = context.applicationContext
        val settingsRepository = EntryPointAccessors
            .fromApplication(appContext, WidgetEntryPoint::class.java)
            .settingsRepository()

        val minutes = settingsRepository.settings.first().rotationIntervalMinutes
            .coerceAtLeast(MIN_PERIOD_MINUTES)
        val workManager = WorkManager.getInstance(appContext)

        if (minutes <= MIN_PERIOD_MINUTES) {
            // The periodic work covers the short intervals.
            workManager.cancelUniqueWork(WORK_NAME_NEXT)
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WidgetRotationWorker>(minutes.toLong(), TimeUnit.MINUTES)
                    .build()
            )
        } else {
            // A one time request can wait longer than the periodic minimum.
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            workManager.enqueueUniqueWork(
                WORK_NAME_NEXT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRotationWorker>()
                    .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                    .build()
            )
        }
    }

    /**
     * Schedules the next run when the interval is longer than the periodic
     * minimum.
     *
     * The worker calls this after it ran: the periodic work schedules itself,
     * the delayed one has to be renewed by hand.
     */
    suspend fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val settingsRepository = EntryPointAccessors
            .fromApplication(appContext, WidgetEntryPoint::class.java)
            .settingsRepository()

        val minutes = settingsRepository.settings.first().rotationIntervalMinutes
        if (minutes <= MIN_PERIOD_MINUTES) return

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME_NEXT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRotationWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .build()
        )
    }
}
