package dev.schrammel.brilliantlist.core_data.repository

import dev.schrammel.brilliantlist.core_data.database.AppStateDao
import dev.schrammel.brilliantlist.core_data.database.AppStateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key/value state that must not be readable in plaintext.
 *
 * Unlike [SettingsRepository] (which only holds non-sensitive preferences in
 * DataStore) these values live in the SQLCipher encrypted database.
 */
@Singleton
class AppStateRepository @Inject constructor(
    private val dao: AppStateDao
) {

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun set(key: String, value: String?) {
        dao.upsert(AppStateEntity(key = key, value = value))
    }

    suspend fun remove(key: String) {
        dao.delete(key)
    }
}
