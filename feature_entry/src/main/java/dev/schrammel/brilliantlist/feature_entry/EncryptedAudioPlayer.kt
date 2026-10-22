package dev.schrammel.brilliantlist.feature_entry

import android.media.MediaDataSource
import android.media.MediaPlayer

/**
 * Plays an encrypted audio clip without ever writing plaintext to disk.
 *
 * The clip is decrypted into memory and served to MediaPlayer through a
 * [MediaDataSource], which keeps the at-rest encryption guarantee of
 * ENCRYPTION_MEDIA_PIPELINE.md intact (only RAM holds the plaintext).
 */
class EncryptedAudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    /** Prepares and starts playback. [onStarted] fires once the player is
     *  actually running, [onCompleted] when playback ends or fails. */
    fun play(
        source: AudioSource,
        onStarted: () -> Unit,
        onCompleted: () -> Unit
    ): Boolean {
        stop()
        val bytes = when (source) {
            is AudioSource.Encoded -> source.bytes
            is AudioSource.Pcm -> Wav.wrap(
                source.bytes,
                source.sampleRate,
                source.channels,
                source.bitsPerSample
            )
        }
        if (bytes.isEmpty()) return false
        return runCatching {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(InMemoryMediaDataSource(bytes))
            player.setOnPreparedListener {
                runCatching { it.start() }
                onStarted()
            }
            player.setOnCompletionListener {
                onCompleted()
                stop()
            }
            player.setOnErrorListener { _, _, _ ->
                onCompleted()
                stop()
                true
            }
            player.prepareAsync()
            true
        }.getOrElse {
            stop()
            false
        }
    }

    /** Playback position in milliseconds, 0 when nothing is playing. */
    fun currentPositionMs(): Int = runCatching {
        mediaPlayer?.currentPosition ?: 0
    }.getOrDefault(0)

    /** Duration of the loaded clip in milliseconds, 0 while it is unknown. */
    fun durationMs(): Int = runCatching {
        mediaPlayer?.duration ?: 0
    }.getOrDefault(0)

    /** Moves the playback position, in milliseconds. */
    fun seekTo(positionMs: Int) {
        runCatching { mediaPlayer?.seekTo(positionMs) }
    }

    /** Stops playback and releases the player. */
    fun stop() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }
}

/**
 * Serves already decrypted bytes to MediaPlayer.
 *
 * MediaPlayer needs random access (it seeks inside the media), so the data is
 * held in memory and served by offset.
 */
private class InMemoryMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {

    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int
    ): Int {
        if (position >= bytes.size) return -1
        val available = bytes.size - position
        val count = minOf(size.toLong(), available).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() {
        // Nothing to release: the bytes are owned by the caller.
    }
}
