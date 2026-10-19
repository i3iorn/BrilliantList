package dev.schrammel.brilliantlist.core_data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE isDeleted = 0 ORDER BY position ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: UUID): EntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("UPDATE entries SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: UUID, timestamp: Long)

    @Query("UPDATE entries SET position = :position, updatedAt = :timestamp WHERE id = :id")
    suspend fun updatePosition(id: UUID, position: Int, timestamp: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM entries")
    suspend fun nextPosition(): Int

    @Query("SELECT * FROM entries WHERE isDeleted = 0 AND type = :type ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomByType(type: String): EntryEntity?
}
