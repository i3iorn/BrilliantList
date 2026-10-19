package dev.schrammel.brilliantlist.core_data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.room.withTransaction
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.EntryDao
import dev.schrammel.brilliantlist.core_data.mapper.toDomain
import dev.schrammel.brilliantlist.core_data.mapper.toEntity
import dev.schrammel.brilliantlist.core_encryption.CryptoManager
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository over the encrypted Room database. Hides SQLCipher/Room details
 * from feature modules and maps EntryEntity <-> core_model.Entry.
 *
 * Media pipeline (ENCRYPTION_MEDIA_PIPELINE.md):
 * - Content streams are AES-GCM encrypted on write into files/media/<uuid>.enc
 * - Decryption happens on demand via a CipherInputStream; never persisted raw.
 */
@Singleton
class EntryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
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

    /**
     * Persists an image entry. The source stream (usually a ContentResolver stream)
     * is encrypted directly without full in-memory buffering.
     */
    suspend fun addImageEntry(content: InputStream): Entry {
        val entry = newEntry(EntryType.IMAGE)
        val encryptedFile = mediaFile(entry.id)
        content.use { input ->
            CryptoManager.encrypt(input, encryptedFile.outputStream())
        }
        dao.upsert(entry.toEntity())
        return entry
    }

    /**
     * Persists an audio entry from a finished recording temp file.
     * Encrypts the temp file, then deletes the raw recording immediately.
     */
    suspend fun addAudioEntry(rawTempFile: File): Entry {
        val entry = newEntry(EntryType.AUDIO)
        val encryptedFile = mediaFile(entry.id)
        rawTempFile.inputStream().use { input ->
            CryptoManager.encrypt(input, encryptedFile.outputStream())
        }
        rawTempFile.delete()
        dao.upsert(entry.toEntity())
        return entry
    }

    /** Opens the decrypted media stream for playback/render. Caller must close. */
    fun openDecryptedMedia(entry: Entry): InputStream? {
        val file = mediaFile(entry.id)
        if (!file.exists()) return null
        return CryptoManager.decryptToStream(file.inputStream())
    }

    private fun mediaFile(id: UUID): File {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        return File(dir, "$id.enc")
    }

    private suspend fun newEntry(type: EntryType): Entry {
        val now = System.currentTimeMillis()
        return Entry(
            id = UUID.randomUUID(),
            type = type,
            createdAt = now,
            updatedAt = now,
            position = dao.nextPosition()
        )
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

    /**
     * Rotation-engine pick honoring Settings.rotationMode.
     * RANDOM: uniform random pick. SEQUENTIAL: next entry after the last shown one
     * (wraps around to the first).
     */
    suspend fun pickForRotation(
        type: EntryType,
        mode: dev.schrammel.brilliantlist.core_model.RotationMode,
        lastShownId: String?
    ): Entry? {
        val ordered = dao.activeByTypeOrdered(type.name)
        if (ordered.isEmpty()) return null
        return when (mode) {
            dev.schrammel.brilliantlist.core_model.RotationMode.RANDOM ->
                ordered.random().toDomain()
            dev.schrammel.brilliantlist.core_model.RotationMode.SEQUENTIAL -> {
                val last = lastShownId?.let { id -> ordered.indexOfFirst { it.id.toString() == id } }
                val nextIndex = if (last == null || last < 0) 0 else (last + 1) % ordered.size
                ordered[nextIndex].toDomain()
            }
        }
    }
}
