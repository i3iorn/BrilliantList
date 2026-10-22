package dev.schrammel.brilliantlist.feature_entry

/**
 * The audio of an entry, decrypted and ready to be played.
 *
 * The recordings of the first versions were M4A containers, the recorder now
 * writes raw PCM samples. Both can be played, the player only needs to know
 * which one it is.
 */
sealed interface AudioSource {

    /** A compressed container (M4A). */
    class Encoded(val bytes: ByteArray) : AudioSource

    /**
     * Raw PCM samples.
     *
     * A WAV header is added when it is played, because the size of the
     * recording is not known while it is recorded.
     */
    class Pcm(
        val bytes: ByteArray,
        val sampleRate: Int = AudioRecorder.SAMPLE_RATE,
        val channels: Int = 1,
        val bitsPerSample: Int = 16
    ) : AudioSource
}

/**
 * Builds the header of a WAV file.
 *
 * The header describes the format of the samples and the size of the data
 * block, which is why it can only be written when the recording is finished.
 */
internal object Wav {

    private const val HEADER_SIZE = 44

    /** The header plus the samples, ready for MediaPlayer. */
    fun wrap(samples: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val header = header(samples.size, sampleRate, channels, bitsPerSample)
        return header + samples
    }

    private fun header(
        dataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArray(HEADER_SIZE)
        var index = 0

        fun ascii(text: String) {
            text.forEach { char -> out[index++] = char.code.toByte() }
        }

        fun leInt(value: Int) {
            out[index++] = (value and 0xFF).toByte()
            out[index++] = ((value shr 8) and 0xFF).toByte()
            out[index++] = ((value shr 16) and 0xFF).toByte()
            out[index++] = ((value shr 24) and 0xFF).toByte()
        }

        fun leShort(value: Int) {
            out[index++] = (value and 0xFF).toByte()
            out[index++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF")
        leInt(36 + dataSize)
        ascii("WAVE")
        ascii("fmt ")
        leInt(16)              // the size of the format block
        leShort(1)             // PCM
        leShort(channels)
        leInt(sampleRate)
        leInt(byteRate)
        leShort(blockAlign)
        leShort(bitsPerSample)
        ascii("data")
        leInt(dataSize)
        return out
    }
}
