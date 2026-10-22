package dev.schrammel.brilliantlist.core_data.media

import dev.schrammel.brilliantlist.core_model.Entry
import java.io.File
import java.io.OutputStream

/**
 * An open, encrypted media file that an entry is written into.
 *
 * The bytes that are written to [output] are encrypted on the way to the
 * disk, so a recording never exists as plaintext. The entry itself is only
 * written to the database when the recording is finished
 * ([dev.schrammel.brilliantlist.core_data.repository.EntryRepository.completeMediaSink]).
 */
class MediaSink internal constructor(
    /** The entry the media belongs to. It is not persisted yet. */
    val entry: Entry,
    private val file: File,
    private val stream: OutputStream
) {

    /** The encrypting stream the media bytes are written to. */
    val output: OutputStream get() = stream

    /** Closes the stream. Closing writes the GCM tag, so it must happen. */
    fun close() {
        runCatching { stream.close() }
    }

    /** Closes the stream and removes the incomplete file. */
    fun abort() {
        close()
        file.delete()
    }
}
