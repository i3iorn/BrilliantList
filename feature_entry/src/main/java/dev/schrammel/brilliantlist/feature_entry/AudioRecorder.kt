package dev.schrammel.brilliantlist.feature_entry

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.OutputStream

/**
 * Records a short audio clip.
 *
 * The samples are written straight into the stream the caller provides, and
 * that stream is the encrypted media file (see
 * [dev.schrammel.brilliantlist.core_data.repository.EntryRepository.openMediaSink]).
 * So the recording never exists as plaintext on the disk - not even while it
 * is being recorded.
 *
 * The format is raw PCM. A WAV header is added when the clip is played, so
 * the file stays headerless (the size is not known while recording).
 *
 * Device hardware can fail (mic busy, codec error), so every recorder call is
 * guarded and a failed recording never propagates.
 */
class AudioRecorder(private val context: Context) {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var output: OutputStream? = null

    var isRecording by mutableStateOf(false)
        private set

    /** The seconds that have been recorded, for the recording indicator. */
    var elapsedSeconds by mutableIntStateOf(0)
        private set

    /**
     * True when the recording stopped because it reached the maximum length.
     *
     * The recording is kept, the user only has to know that it was cut off.
     */
    var limitReached by mutableStateOf(false)
        private set

    /**
     * Starts recording into [output].
     *
     * Returns false when the microphone is not available; nothing is written
     * in that case.
     */
    fun start(output: OutputStream): Boolean {
        if (isRecording) return false
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 2

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }.getOrNull() ?: return false

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            return false
        }

        val started = runCatching { recorder.startRecording() }.isSuccess
        if (!started) {
            runCatching { recorder.release() }
            return false
        }

        record = recorder
        this.output = output
        elapsedSeconds = 0
        limitReached = false
        isRecording = true

        val worker = Thread { readLoop(recorder, bufferSize) }
        thread = worker
        worker.start()
        return true
    }

    /**
     * Stops the recording and closes the stream.
     *
     * Closing is important: it writes the authentication tag of the encrypted
     * file, without it the recording cannot be decrypted.
     */
    fun stop() {
        val recorder = record
        val worker = thread
        val stream = output
        record = null
        thread = null
        output = null
        isRecording = false

        runCatching { recorder?.stop() }
        // Give the reader a moment to flush the last buffer.
        runCatching { worker?.join(500) }
        runCatching { recorder?.release() }
        runCatching { stream?.close() }
    }

    /** Stops the recording and releases the microphone. */
    fun dispose() {
        stop()
    }

    /**
     * Reads the samples and writes them into the stream.
     *
     * The loop ends when the recorder is stopped or when the stream fails;
     * a broken stream must not crash the app.
     */
    private fun readLoop(recorder: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var totalBytes = 0L
        val bytesPerSecond = SAMPLE_RATE * BYTES_PER_SAMPLE
        val maxBytes = MAX_DURATION_SECONDS * bytesPerSecond
        while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(-1)
            if (read <= 0) {
                if (read < 0) break
                continue
            }
            val stream = output ?: break
            // write() returns nothing, a broken stream shows up as an error.
            val written = runCatching { stream.write(buffer, 0, read) }.isSuccess
            if (!written) break
            totalBytes += read
            elapsedSeconds = (totalBytes / bytesPerSecond).toInt()
            if (totalBytes >= maxBytes) {
                // The recording is long enough. The screen saves it and tells
                // the user that it was cut off.
                limitReached = true
                break
            }
        }
    }

    companion object {
        /** The sample rate of the recordings. */
        const val SAMPLE_RATE = 44100

        /**
         * The longest recording the app makes, in seconds.
         *
         * The recording is raw PCM, so it is much bigger than a compressed
         * one and it is loaded into memory to be played. A limit keeps a note
         * a reasonable size - and it keeps a forgotten recording from filling
         * the disk.
         */
        const val MAX_DURATION_SECONDS = 120

        /** The recordings are mono. */
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        /** The recordings are 16 bit signed PCM. */
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** The number of bytes of one sample (two for 16 bit mono). */
        const val BYTES_PER_SAMPLE = 2

        private const val TEMP_PREFIX = "rec_"

        /**
         * Deletes leftover raw recordings of older versions.
         *
         * The recorder now writes straight into the encrypted file, so there
         * is no temp file any more. Recordings of older versions can still be
         * in the cache, so they are swept on app start.
         */
        fun purgeStaleRecordings(context: Context) {
            val cache = context.cacheDir
            val leftovers = cache.listFiles { file -> file.name.startsWith(TEMP_PREFIX) }
                ?: return
            leftovers.forEach { file -> runCatching { file.delete() } }
        }
    }
}
