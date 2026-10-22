package dev.schrammel.brilliantlist.core_data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.room.withTransaction
import dev.schrammel.brilliantlist.core_data.backup.BackupContainer
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_data.database.EntryDao
import dev.schrammel.brilliantlist.core_data.mapper.toDomain
import dev.schrammel.brilliantlist.core_data.mapper.toEntity
import dev.schrammel.brilliantlist.core_data.media.ExifStripper
import dev.schrammel.brilliantlist.core_data.media.MediaSink
import dev.schrammel.brilliantlist.core_encryption.CryptoManager
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.AudioFormat
import dev.schrammel.brilliantlist.core_model.EntryType
import dev.schrammel.brilliantlist.core_model.JournalStats
import dev.schrammel.brilliantlist.core_model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
    private val dao: EntryDao,
    private val keyManager: DatabaseKeyManager
) {

    /**
     * The entry list in the requested order.
     *
     * MANUAL keeps the order the user dragged the entries into,
     * NEWEST_FIRST sorts by creation date with the newest entry on top.
     * A blank [query] shows everything.
     */
    fun observeEntries(sortMode: SortMode = SortMode.MANUAL, query: String = ""): Flow<List<Entry>> {
        val trimmed = query.trim()
        val source = when {
            trimmed.isNotEmpty() && sortMode == SortMode.MANUAL -> dao.search(trimmed)
            trimmed.isNotEmpty() -> dao.searchByNewest(trimmed)
            sortMode == SortMode.MANUAL -> dao.observeAll()
            else -> dao.observeAllByNewest()
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    /** The soft deleted entries, newest deletion first. */
    fun observeDeleted(): Flow<List<Entry>> =
        dao.observeDeleted().map { list -> list.map { it.toDomain() } }

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
     *
     * The EXIF metadata is removed on the way in: a photo carries the location
     * of the person and the device, and that has no place in a journal.
     *
     * The work runs on the IO dispatcher: reading a photo, removing the
     * metadata and encrypting it must never happen on the main thread.
     */
    suspend fun addImageEntry(content: InputStream): Entry = withContext(Dispatchers.IO) {
        val entry = newEntry(EntryType.IMAGE)
        val encryptedFile = mediaFile(entry)
        content.use { input ->
            val stripped = ExifStripper.strip(input)
            CryptoManager.encryptWithKey(keyManager.activeKey(), stripped, encryptedFile.outputStream())
        }
        dao.upsert(entry.toEntity())
        entry
    }

    /**
     * Persists an audio entry from a finished recording temp file.
     * Encrypts the temp file, then deletes the raw recording immediately.
     */
    suspend fun addAudioEntry(rawTempFile: File): Entry = withContext(Dispatchers.IO) {
        val entry = newEntry(EntryType.AUDIO)
        val encryptedFile = mediaFile(entry)
        rawTempFile.inputStream().use { input ->
            CryptoManager.encryptWithKey(keyManager.activeKey(), input, encryptedFile.outputStream())
        }
        rawTempFile.delete()
        dao.upsert(entry.toEntity())
        entry
    }

    /**
     * Opens the decrypted media stream for playback/render. Caller must close.
     *
     * Fails closed: a missing, truncated or corrupted file yields null instead
     * of throwing, so a broken file can never crash the UI.
     */
    fun openDecryptedMedia(entry: Entry): InputStream? {
        val file = mediaFile(entry)
        if (!file.exists()) return null
        return runCatching {
            CryptoManager.decryptToStreamWithKey(keyManager.activeKey(), file.inputStream())
        }.getOrNull()
    }

    /** Marks an entry as favorite (or clears the flag). */
    suspend fun setFavorite(id: UUID, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    /**
     * Updates the text of an entry. For image and audio entries this is the
     * caption; for text entries it is the content.
     */
    suspend fun updateText(id: UUID, text: String?) {
        dao.updateText(id, text, System.currentTimeMillis())
    }

    /**
     * Undoes a soft delete. The entity is written back with isDeleted = false;
     * the original position and timestamps are preserved.
     */
    suspend fun restore(entry: Entry) {
        dao.restore(entry.id, System.currentTimeMillis())
    }

    /** Marks the entry deleted (soft delete, no data loss). */
    suspend fun softDelete(id: UUID) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    /** Removes an entry and its media for good. */
    suspend fun deletePermanently(entry: Entry) {
        dao.deletePermanently(entry.id)
        mediaFile(entry).delete()
    }

    /**
     * Removes the entries that have been in the trash for longer than
     * [maxAgeMillis], including their media files.
     *
     * @return the number of removed entries.
     */
    suspend fun purgeDeleted(maxAgeMillis: Long): Int {
        val threshold = System.currentTimeMillis() - maxAgeMillis
        val removed = dao.purgeDeletedBefore(threshold)
        // The media files of the removed rows are gone from the database, so
        // they are cleaned up by looking at what is left on disk.
        sweepOrphanedMedia()
        return removed
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

    /** The first active entry of the given type, or null. */
    suspend fun firstOfType(type: EntryType): Entry? =
        dao.firstOfType(type.name)?.toDomain()

    /**
     * Rotation-engine pick honoring Settings.rotationMode.
     * RANDOM: uniform random pick. SEQUENTIAL: next entry after the last shown one
     * (wraps around to the first).
     */
    suspend fun pickForRotation(
        type: EntryType,
        mode: dev.schrammel.brilliantlist.core_model.RotationMode,
        lastShownId: String?,
        sortMode: SortMode = SortMode.MANUAL
    ): Entry? {
        val ordered = when (sortMode) {
            SortMode.MANUAL -> dao.activeByTypeOrdered(type.name)
            SortMode.NEWEST_FIRST -> dao.activeByTypeOrderedByNewest(type.name)
        }
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

    /**
     * Writes the journal into [output] as an encrypted export.
     *
     * The export is encrypted with the passphrase the user chose, so it can be
     * restored on another device. The entries are streamed, so the export
     * does not need the whole journal in memory.
     */
    suspend fun exportTo(output: OutputStream, passphrase: CharArray): Int =
        withContext(Dispatchers.IO) {
        val sources = dao.activeEntries().map { entity ->
            val entry = entity.toDomain()
            val file = mediaFile(entry)
            // The ciphertext has the same length as the plaintext, the IV and
            // the tag are added.
            val mediaSize = if (file.exists()) {
                (file.length() - CryptoManager.OVERHEAD).coerceAtLeast(0L).toInt()
            } else {
                0
            }
            BackupContainer.EntrySource(
                type = entry.type,
                text = entry.textContent,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                position = entry.position,
                isFavorite = entry.isFavorite,
                mediaSize = mediaSize,
                media = { if (mediaSize > 0) openDecryptedMedia(entry) else null }
            )
        }
        BackupContainer.write(output, passphrase, sources)
        sources.size
    }

    /**
     * Writes the journal into [uri] and reads the file back.
     *
     * A backup that cannot be read is worse than no backup: it gives the
     * feeling of safety. The file is read, not written again, so the check
     * costs one decryption.
     *
     * @return the number of entries in the file, or -1 when the file could
     * not be read back.
     */
    suspend fun exportTo(uri: Uri, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        // The export clears the passphrase it is given, so the check gets a
        // copy of its own.
        val forWriting = passphrase.copyOf()
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                exportTo(output, forWriting)
            } ?: error("the file could not be opened")
        }.getOrElse {
            forWriting.fill('\u0000')
            return@withContext -1
        }

        val forReading = passphrase.copyOf()
        val read = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var count = 0
                BackupContainer.read(input, forReading) { _, _ -> count++ }
                count
            } ?: error("the file could not be read")
        }.getOrDefault(-1)
        forReading.fill('\u0000')

        if (read == written) written else -1
    }

    /**
     * Restores a journal from an encrypted export.
     *
     * The entries are added at the end of the list, so an import never
     * overwrites what is already there. The export is read entry by entry,
     * so an import does not need the whole file in memory either.
     *
     * The import is atomic: either all the entries are restored or none of
     * them. A half imported journal would be worse than a failed import.
     *
     * @return the number of restored entries.
     */
    suspend fun importFrom(input: InputStream, passphrase: CharArray): Int =
        withContext(Dispatchers.IO) {
            var position = dao.nextPosition()
            val restored = mutableListOf<Entry>()
            BackupContainer.read(input, passphrase) { backup, media ->
                val entry = Entry(
                    id = UUID.randomUUID(),
                    type = backup.type,
                    textContent = backup.text,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt,
                    position = position++
                )
                if (media != null) {
                    val file = mediaFile(entry)
                    CryptoManager.encryptWithKey(
                        keyManager.activeKey(),
                        media,
                        file.outputStream()
                    )
                }
                restored += entry.copy(isFavorite = backup.isFavorite)
            }
            // One transaction for all the entries: an import that fails in
            // the middle leaves nothing behind.
            database.withTransaction {
                restored.forEach { dao.upsert(it.toEntity()) }
            }
            restored.size
        }

    /**
     * What the journal takes up on the device.
     *
     * The numbers are read when they are asked for, not watched: they change
     * rarely and the reading touches the disk.
     */
    suspend fun journalStats(): JournalStats = withContext(Dispatchers.IO) {
        val media = mediaDirectory().listFiles()?.sumOf { it.length() } ?: 0L
        val database = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            .let { if (it.exists()) it.length() else 0L }
        JournalStats(
            entryCount = dao.countActive(),
            mediaBytes = media,
            databaseBytes = database
        )
    }

    /**
     * Deletes every entry and every media file.
     *
     * Used by "erase everything": the key material is removed by the caller
     * ([DatabaseKeyManager.wipe]), this only takes care of the content.
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.deleteAll()
        }
        mediaDirectory().listFiles()?.forEach { it.delete() }
    }

    /**
     * Erases everything: the entries, the media files and the key.
     *
     * The key is replaced instead of only deleted, so the database stays
     * readable - it is empty anyway. The old key, which protected the deleted
     * content, is gone.
     */
    suspend fun eraseEverything() = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.deleteAll()
        }
        mediaDirectory().listFiles()?.forEach { it.delete() }
        keyManager.rotateKeyToNewAlias()
    }

    /**
     * Opens an encrypted media file for a new entry.
     *
     * The bytes written to the sink are encrypted on the way to the disk, so
     * a recording never exists as plaintext. The entry is not persisted until
     * [completeMediaSink] is called.
     */
    suspend fun openMediaSink(type: EntryType): MediaSink = withContext(Dispatchers.IO) {
        val entry = newEntry(type, mediaSuffixFor(type))
        val file = mediaFile(entry)
        val stream = CryptoManager.encryptToStream(keyManager.activeKey(), file.outputStream())
        MediaSink(entry, file, stream)
    }

    /** Persists the entry of a finished [sink]. */
    suspend fun completeMediaSink(sink: MediaSink): Entry {
        sink.close()
        dao.upsert(sink.entry.toEntity())
        return sink.entry
    }

    /** The format of the audio media of an entry. */
    fun audioFormat(entry: Entry): AudioFormat =
        if (entry.mediaUri?.endsWith(PCM_MEDIA_SUFFIX) == true) {
            AudioFormat.PCM
        } else {
            AudioFormat.ENCODED
        }

    /**
     * The size of the plaintext media of an entry, in bytes.
     *
     * The size of the encrypted file is known without decrypting it, so the
     * length of a recording can be shown before it is played.
     */
    fun mediaSize(entry: Entry): Long {
        val file = mediaFile(entry)
        if (!file.exists()) return 0
        return (file.length() - CryptoManager.OVERHEAD).coerceAtLeast(0L)
    }

    /**
     * Resolves the encrypted media file of an entry. Entries created before
     * mediaUri was recorded fall back to the id derived path.
     */
    private fun mediaFile(entry: Entry): File {
        val relative = entry.mediaUri ?: "$MEDIA_DIR/${entry.id}$MEDIA_SUFFIX"
        val file = File(context.filesDir, relative)
        file.parentFile?.mkdirs()
        return file
    }

    private fun mediaDirectory(): File = File(context.filesDir, MEDIA_DIR).also { it.mkdirs() }

    /**
     * Deletes the media files that do not belong to an entry any more.
     *
     * A recording that was interrupted - the process was killed while the
     * microphone was on - leaves a file behind: the entry was never written,
     * so nothing points to the file any more.
     */
    suspend fun sweepOrphanedMedia() {
        val known = dao.allMediaUris().toSet()
        mediaDirectory().listFiles()?.forEach { file ->
            val relative = "$MEDIA_DIR/${file.name}"
            if (relative !in known) {
                file.delete()
            }
        }
    }

    private suspend fun newEntry(type: EntryType, suffix: String = MEDIA_SUFFIX): Entry {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID()
        return Entry(
            id = id,
            type = type,
            // Text entries have no media file; the path is recorded for everything else.
            mediaUri = if (type == EntryType.TEXT) null else "$MEDIA_DIR/$id$suffix",
            createdAt = now,
            updatedAt = now,
            position = dao.nextPosition()
        )
    }

    /** The file suffix of the media of an entry of the given type. */
    private fun mediaSuffixFor(type: EntryType): String = when (type) {
        EntryType.AUDIO -> PCM_MEDIA_SUFFIX
        else -> MEDIA_SUFFIX
    }

    private companion object {
        const val MEDIA_DIR = "media"
        const val MEDIA_SUFFIX = ".enc"

        /**
         * The suffix of the raw PCM recordings.
         *
         * It marks the format of the media, so the old M4A recordings and the
         * new PCM ones can be told apart.
         */
        const val PCM_MEDIA_SUFFIX = ".pcm.enc"
    }
}

