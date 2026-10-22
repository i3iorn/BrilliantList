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

    @Query("SELECT * FROM entries WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllByNewest(): Flow<List<EntryEntity>>

    /**
     * Free text search over the entry text.
     *
     * The query runs inside SQLCipher, so the plaintext never leaves the
     * encrypted database.
     */
    @Query(
        "SELECT * FROM entries WHERE isDeleted = 0 " +
            "AND (textContent LIKE '%' || :query || '%' OR type LIKE '%' || :query || '%') " +
            "ORDER BY position ASC"
    )
    fun search(query: String): Flow<List<EntryEntity>>

    @Query(
        "SELECT * FROM entries WHERE isDeleted = 0 " +
            "AND (textContent LIKE '%' || :query || '%' OR type LIKE '%' || :query || '%') " +
            "ORDER BY createdAt DESC"
    )
    fun searchByNewest(query: String): Flow<List<EntryEntity>>

    /** The soft deleted entries, newest deletion first. */
    @Query("SELECT * FROM entries WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun observeDeleted(): Flow<List<EntryEntity>>

    /** Every active entry, for the encrypted export. */
    @Query("SELECT * FROM entries WHERE isDeleted = 0 ORDER BY position ASC")
    suspend fun activeEntries(): List<EntryEntity>

    /** Every entry, deleted ones included. */
    @Query("SELECT * FROM entries")
    suspend fun allEntries(): List<EntryEntity>

    /** The paths of the media files that belong to an entry. */
    @Query("SELECT mediaUri FROM entries WHERE mediaUri IS NOT NULL")
    suspend fun allMediaUris(): List<String>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: UUID): EntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("UPDATE entries SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: UUID, timestamp: Long)

    @Query("UPDATE entries SET isDeleted = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun restore(id: UUID, timestamp: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deletePermanently(id: UUID)

    /** Removes every entry, used by "erase everything". */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Query("DELETE FROM entries WHERE isDeleted = 1 AND updatedAt < :timestamp")
    suspend fun purgeDeletedBefore(timestamp: Long): Int

    @Query("UPDATE entries SET position = :position, updatedAt = :timestamp WHERE id = :id")
    suspend fun updatePosition(id: UUID, position: Int, timestamp: Long)

    @Query("UPDATE entries SET isFavorite = :favorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: UUID, favorite: Boolean, timestamp: Long)

    /** Updates the text of an entry (also used as the caption of an image/audio entry). */
    @Query("UPDATE entries SET textContent = :text, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateText(id: UUID, text: String?, timestamp: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM entries")
    suspend fun nextPosition(): Int

    @Query("SELECT * FROM entries WHERE isDeleted = 0 AND type = :type ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomByType(type: String): EntryEntity?

    /** The first active entry of a type, without loading the whole list. */
    @Query("SELECT * FROM entries WHERE isDeleted = 0 AND type = :type ORDER BY position ASC LIMIT 1")
    suspend fun firstOfType(type: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE isDeleted = 0 AND type = :type ORDER BY position ASC")
    suspend fun activeByTypeOrdered(type: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE isDeleted = 0 AND type = :type ORDER BY createdAt DESC")
    suspend fun activeByTypeOrderedByNewest(type: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun observeById(id: UUID): Flow<EntryEntity?>
}
