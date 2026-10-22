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
import dev.schrammel.brilliantlist.core_model.CrisisRegion
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.Settings
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists app settings in an encrypted-at-rest-compatible way: DataStore holds only
 * non-sensitive preferences (no keys, no entry content, no entry ids). SQLCipher
 * protects content data; state that references an entry lives in [AppStateRepository].
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ROTATION_MODE = stringPreferencesKey("rotation_mode")
        val ROTATION_INTERVAL = intPreferencesKey("rotation_interval_minutes")
        val WIDGET_AUTO_UPDATE = booleanPreferencesKey("widget_auto_update")
        val CRISIS_REGION = stringPreferencesKey("crisis_region")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val WIDGET_SHOW_CONTENT = booleanPreferencesKey("widget_show_content")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { prefs ->
        Settings(
            rotationMode = prefs[Keys.ROTATION_MODE]?.let { RotationMode.valueOf(it) }
                ?: RotationMode.RANDOM,
            rotationIntervalMinutes = prefs[Keys.ROTATION_INTERVAL] ?: 30,
            widgetAutoUpdate = prefs[Keys.WIDGET_AUTO_UPDATE] ?: true,
            crisisRegion = prefs[Keys.CRISIS_REGION] ?: CrisisRegion.DEFAULT.code,
            sortMode = prefs[Keys.SORT_MODE]?.let { SortMode.valueOf(it) } ?: SortMode.MANUAL,
            // Privacy by default: journal text does not end up on the home
            // screen until the user asks for it.
            widgetShowContent = prefs[Keys.WIDGET_SHOW_CONTENT] ?: false,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: true
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

    suspend fun setSortMode(mode: SortMode) {
        context.settingsStore.edit { it[Keys.SORT_MODE] = mode.name }
    }

    suspend fun setWidgetShowContent(show: Boolean) {
        context.settingsStore.edit { it[Keys.WIDGET_SHOW_CONTENT] = show }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }
}
