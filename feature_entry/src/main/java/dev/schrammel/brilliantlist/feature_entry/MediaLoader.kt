package dev.schrammel.brilliantlist.feature_entry

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_model.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes and decodes the media of an entry.
 *
 * The encrypted file is streamed through CryptoManager, so the plaintext only
 * ever exists in memory while it is being decoded.
 */
@Singleton
class MediaLoader @Inject constructor(
    private val repository: EntryRepository
) {

    /**
     * The decoded thumbnails.
     *
     * Without the cache every scroll decodes the photos again, which is the
     * most expensive thing the list does. The cache holds the small bitmaps
     * only, and only as many as fit into the budget.
     */
    private val thumbnails = ThumbnailCache()

    /**
     * Decodes a downscaled bitmap for [entry].
     * Returns null when the media is missing, undecryptable or not an image.
     */
    suspend fun thumbnail(entry: Entry, targetWidthPx: Int): Bitmap? {
        val key = ThumbnailKey(entry.id, targetWidthPx)
        thumbnails.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(entry, targetWidthPx) }
        if (bitmap != null) {
            thumbnails.put(key, bitmap)
        }
        return bitmap
    }

    /**
     * Decrypts the image once and decodes it from the buffer.
     *
     * The file is decrypted only once: the bounds and the bitmap are read
     * from the same bytes.
     */
    private fun decodeThumbnail(entry: Entry, targetWidthPx: Int): Bitmap? = runCatching {
        // Fail closed: a corrupt file (bad GCM tag, broken stream) must not
        // crash the screen that renders it.
        val bytes = repository.openDecryptedMedia(entry)?.use { it.readBytes() }
            ?: return@runCatching null
        if (bytes.isEmpty()) return@runCatching null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, targetWidthPx)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    /**
     * Decrypts the audio clip of [entry] into memory. The bytes are never
     * written to disk in plaintext.
     */
    suspend fun audioSource(entry: Entry): AudioSource? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = repository.openDecryptedMedia(entry)?.use { stream -> stream.readBytes() }
                ?: return@runCatching null
            when (repository.audioFormat(entry)) {
                dev.schrammel.brilliantlist.core_model.AudioFormat.PCM ->
                    AudioSource.Pcm(bytes)

                dev.schrammel.brilliantlist.core_model.AudioFormat.ENCODED ->
                    AudioSource.Encoded(bytes)
            }
        }.getOrNull()
    }

    /**
     * The length of the audio clip of an entry, in milliseconds.
     *
     * The length of a raw PCM recording is known from the size of the file,
     * so it can be shown before the clip is played. A compressed recording
     * only knows its length once the player prepared it.
     */
    suspend fun audioDurationMs(entry: Entry): Int = withContext(Dispatchers.IO) {
        when (repository.audioFormat(entry)) {
            dev.schrammel.brilliantlist.core_model.AudioFormat.PCM -> {
                val bytesPerSecond = AudioRecorder.SAMPLE_RATE *
                    AudioRecorder.BYTES_PER_SAMPLE
                (repository.mediaSize(entry) * 1000 / bytesPerSecond).toInt()
            }

            dev.schrammel.brilliantlist.core_model.AudioFormat.ENCODED -> 0
        }
    }
}

/**
 * The key of a cached thumbnail.
 *
 * The width is part of the key: the same photo is decoded again when the
 * screen gets wider.
 */
private data class ThumbnailKey(val id: UUID, val widthPx: Int)

/**
 * A small cache of the decoded thumbnails.
 *
 * The bitmaps are the biggest thing the list holds in memory, so the cache
 * has a budget in bytes and forgets the least recently used entries when it
 * is full.
 */
private class ThumbnailCache {

    private val entries = LinkedHashMap<ThumbnailKey, Bitmap>(16, 0.75f, true)
    private var usedBytes = 0

    @Synchronized
    fun get(key: ThumbnailKey): Bitmap? = entries[key]

    @Synchronized
    fun put(key: ThumbnailKey, bitmap: Bitmap) {
        val size = bitmap.allocationByteCount
        if (size > MAX_BYTES) {
            // A single bitmap that does not fit is not cached.
            return
        }
        entries.remove(key)?.let { usedBytes -= it.allocationByteCount }
        entries[key] = bitmap
        usedBytes += size
        while (usedBytes > MAX_BYTES && entries.isNotEmpty()) {
            val oldest = entries.entries.first()
            usedBytes -= oldest.value.allocationByteCount
            entries.remove(oldest.key)
        }
    }

    private companion object {
        /** How much memory the thumbnails may use. */
        const val MAX_BYTES = 16 * 1024 * 1024
    }
}

/**
 * Largest power of two that still covers [targetWidthPx], so bitmaps are
 * decoded at roughly the size they are displayed at instead of full size.
 */
internal fun sampleSize(sourceWidth: Int, targetWidthPx: Int): Int {
    if (sourceWidth <= 0 || targetWidthPx <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidthPx) {
        sample *= 2
    }
    return sample
}
