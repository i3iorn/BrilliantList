package dev.schrammel.brilliantlist.core_data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists app settings in an encrypted-at-rest-compatible way: DataStore holds only
 * non-sensitive preferences (no keys, no entry content). SQLCipher protects content data.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ROTATION_MODE = stringPreferencesKey("rotation_mode")
        val ROTATION_INTERVAL = intPreferencesKey("rotation_interval_minutes")
        val WIDGET_AUTO_UPDATE = booleanPreferencesKey("widget_auto_update")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CRISIS_REGION = stringPreferencesKey("crisis_region")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val LAST_SHOWN_ENTRY = stringPreferencesKey("last_shown_entry_id")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { prefs ->
        Settings(
            rotationMode = prefs[Keys.ROTATION_MODE]?.let { RotationMode.valueOf(it) }
                ?: RotationMode.RANDOM,
            rotationIntervalMinutes = prefs[Keys.ROTATION_INTERVAL] ?: 30,
            widgetAutoUpdate = prefs[Keys.WIDGET_AUTO_UPDATE] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            crisisRegion = prefs[Keys.CRISIS_REGION] ?: "US",
            encryptionEnabled = prefs[Keys.ENCRYPTION_ENABLED] ?: true
        )
    }

    suspend fun setRotationMode(mode: RotationMode) {
        context.settingsStore.edit { it[Keys.ROTATION_MODE] = mode.name }
    }

    suspend fun setRotationIntervalMinutes(minutes: Int) {
        context.settingsStore.edit { it[Keys.ROTATION_INTERVAL] = minutes }
    }

    suspend fun setWidgetAutoUpdate(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.WIDGET_AUTO_UPDATE] = enabled }
    }

    suspend fun setCrisisRegion(region: String) {
        context.settingsStore.edit { it[Keys.CRISIS_REGION] = region }
    }

    /** Remembers the entry id surfaced last so SEQUENTIAL rotation can advance. */
    suspend fun setLastShownEntryId(id: String) {
        context.settingsStore.edit { it[Keys.LAST_SHOWN_ENTRY] = id }
    }

    suspend fun lastShownEntryId(): String? =
        context.settingsStore.data.map { it[Keys.LAST_SHOWN_ENTRY] }
            .first() // one-shot read for the worker
}
