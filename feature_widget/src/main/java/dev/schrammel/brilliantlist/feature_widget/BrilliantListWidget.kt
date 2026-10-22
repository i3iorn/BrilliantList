package dev.schrammel.brilliantlist.feature_widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hilt entry point so the Glance receiver can reach the SingletonComponent graph
 * (Glance receivers can't use @AndroidEntryPoint).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun entryRepository(): EntryRepository
    fun settingsRepository(): SettingsRepository
    fun databaseKeyManager(): DatabaseKeyManager
}

class BrilliantListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )

        // The user can hide the entry text on the home screen. When the
        // setting cannot be read the text stays hidden: a journal entry on
        // the home screen is a privacy risk, not a feature.
        val showContent = runCatching {
            entryPoint.settingsRepository().settings.first().widgetShowContent
        }.getOrDefault(false)

        // The database can only be opened while the device is unlocked.
        val deviceUnlocked = runCatching {
            entryPoint.databaseKeyManager().isAvailable()
        }.getOrDefault(false)

        var failed = false
        val entry = if (showContent && deviceUnlocked) {
            try {
                val repo = entryPoint.entryRepository()
                // Rotation engine: pick one entry (data must be fetched BEFORE provideContent,
                // whose lambda is not a coroutine scope).
                repo.randomByType(EntryType.TEXT)
                    // Fallback must also be a TEXT entry: image/audio entries have null
                    // textContent and would render the misleading placeholder message.
                    ?: repo.firstOfType(EntryType.TEXT)
            } catch (e: Exception) {
                // The database could not be opened (locked device, corrupted key).
                failed = true
                null
            }
        } else {
            null
        }

        // Tapping the widget opens the app (see README: "tap to open the app").
        // The activity is addressed by name because feature_widget cannot depend
        // on the :app module without creating a dependency cycle.
        val openApp = actionStartActivity(
            Intent().setClassName(context, context.packageName + ".MainActivity")
        )

        val text = when {
            !deviceUnlocked || failed -> context.getString(R.string.widget_locked)
            !showContent -> context.getString(R.string.widget_hidden)
            entry != null -> entry.textContent.orEmpty()
            else -> context.getString(R.string.widget_empty)
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clickable(openApp)
            ) {
                Text(
                    text = text,
                    style = TextStyle(color = ColorProvider(R.color.widget_text))
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
        // The interval is read asynchronously; the worker also re-checks it each run.
        CoroutineScope(Dispatchers.Default).launch {
            WidgetRotationScheduler.schedule(context)
        }
    }
}
