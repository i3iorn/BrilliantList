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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.first

/**
 * Hilt entry point so the Glance receiver can reach the SingletonComponent graph
 * (Glance receivers can't use @AndroidEntryPoint directly).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun entryRepository(): EntryRepository
}

class BrilliantListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Rotation engine: surface one random, non-deleted entry.
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        ).entryRepository()
        val entry = repo.randomByType(EntryType.TEXT)
            ?: repo.observeEntries().first().firstOrNull()

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
}
