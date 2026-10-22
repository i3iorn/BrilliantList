package dev.schrammel.brilliantlist.core_data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppStateDao {

    @Query("SELECT value FROM app_state WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(entity: AppStateEntity)

    @Query("DELETE FROM app_state WHERE key = :key")
    suspend fun delete(key: String)
}
