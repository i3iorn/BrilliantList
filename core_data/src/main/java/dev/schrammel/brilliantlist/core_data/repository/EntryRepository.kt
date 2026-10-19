package dev.schrammel.brilliantlist.core_data.repository

import androidx.room.withTransaction
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.EntryDao
import dev.schrammel.brilliantlist.core_data.mapper.toDomain
import dev.schrammel.brilliantlist.core_data.mapper.toEntity
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository over the encrypted Room database. Hides SQLCipher/Room details
 * from feature modules and maps EntryEntity <-> core_model.Entry.
 */
@Singleton
class EntryRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: EntryDao
) {

    fun observeEntries(): Flow<List<Entry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Adds a text entry and returns the persisted model. */
    suspend fun addTextEntry(text: String?): Entry {
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = UUID.randomUUID(),
            type = EntryType.TEXT,
            textContent = text,
            createdAt = now,
            updatedAt = now,
            position = dao.nextPosition()
        )
        dao.upsert(entry.toEntity())
        return entry
    }

    /** Marks the entry deleted (soft delete, no data loss). */
    suspend fun softDelete(id: UUID) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    /** Persists a new ordering atomically; idsInOrder must contain all active ids. */
    suspend fun reorder(idsInOrder: List<UUID>) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            idsInOrder.forEachIndexed { index, id -> dao.updatePosition(id, index, now) }
        }
    }

    /** Picks a random non-deleted entry of the given type (rotation engine helper). */
    suspend fun randomByType(type: EntryType): Entry? =
        dao.getRandomByType(type.name)?.toDomain()
}
