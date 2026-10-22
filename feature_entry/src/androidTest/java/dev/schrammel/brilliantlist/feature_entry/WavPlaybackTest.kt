package dev.schrammel.brilliantlist.feature_entry

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The recordings are raw PCM; a WAV header is added in memory before they are
 * played. MediaPlayer has to be able to play that through a MediaDataSource -
 * without it the audio of the new recordings would not work at all.
 */
@RunWith(AndroidJUnit4::class)
class WavPlaybackTest {

    @Test
    fun mediaPlayerPlaysAWavStream() {
        val sampleRate = 44100
        val seconds = 1
        val samples = sampleRate * seconds
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (Math.sin(i.toDouble() / 20.0) * 8000).toInt().toShort()
            pcm[i * 2] = (value.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        val wav = wavHeader(pcm.size, sampleRate) + pcm

        val latch = CountDownLatch(1)
        var duration = -1
        var error = ""
        val player = android.media.MediaPlayer()
        player.setDataSource(WavDataSource(wav))
        player.setOnPreparedListener { p ->
            duration = p.duration
            latch.countDown()
        }
        player.setOnErrorListener { _, what, extra ->
            error = "error $what/$extra"
            latch.countDown()
            true
        }
        player.prepareAsync()
        val prepared = latch.await(10, TimeUnit.SECONDS)
        player.release()

        println("PROBE wav prepared=$prepared duration=$duration error=$error size=${wav.size}")
        assertTrue("not prepared: $error", prepared && error.isEmpty())
        assertEquals(1000, duration)
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        fun leShort(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray())
        le(36 + dataSize)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        le(16)
        leShort(1)          // PCM
        leShort(1)          // mono
        le(sampleRate)
        le(sampleRate * 2)  // byte rate
        leShort(2)          // block align
        leShort(16)         // bits per sample
        out.write("data".toByteArray())
        le(dataSize)
        return out.toByteArray()
    }

    private class WavDataSource(private val bytes: ByteArray) : android.media.MediaDataSource() {
        override fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            size: Int
        ): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), (bytes.size - position).toLong()).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}
