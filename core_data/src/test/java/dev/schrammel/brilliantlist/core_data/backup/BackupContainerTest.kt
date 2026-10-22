package dev.schrammel.brilliantlist.core_data.backup

import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The container format is pure crypto, so it runs on the JVM.
 */
class BackupContainerTest {

    private fun source(
        type: EntryType,
        text: String?,
        media: ByteArray?,
        createdAt: Long = 1_000L,
        updatedAt: Long = 2_000L,
        position: Int = 0,
        favorite: Boolean = false
    ) = BackupContainer.EntrySource(
        type = type,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt,
        position = position,
        isFavorite = favorite,
        mediaSize = media?.size ?: 0,
        media = { media?.let { ByteArrayInputStream(it) } }
    )

    @Test
    fun entriesSurviveTheRoundTrip() {
        val media = ByteArray(64) { it.toByte() }
        val entries = listOf(
            source(
                EntryType.TEXT,
                "a brilliant thing",
                null,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                position = 0,
                favorite = true
            ),
            source(
                EntryType.IMAGE,
                "a photo",
                media,
                createdAt = 3_000L,
                updatedAt = 4_000L,
                position = 1
            )
        )

        val out = ByteArrayOutputStream()
        BackupContainer.write(out, "a good passphrase".toCharArray(), entries)

        val read = mutableListOf<BackupContainer.Entry>()
        val mediaOut = mutableListOf<ByteArray?>()
        runBlocking {
            BackupContainer.read(
                ByteArrayInputStream(out.toByteArray()),
                "a good passphrase".toCharArray()
            ) { entry, mediaStream ->
                read += entry
                mediaOut += mediaStream?.use { it.readBytes() }
            }
        }

        assertEquals(2, read.size)
        assertEquals(EntryType.TEXT, read[0].type)
        assertEquals("a brilliant thing", read[0].text)
        assertEquals(1_000L, read[0].createdAt)
        assertEquals(2_000L, read[0].updatedAt)
        assertEquals(0, read[0].position)
        assertTrue(read[0].isFavorite)
        assertNull(mediaOut[0])

        assertEquals(EntryType.IMAGE, read[1].type)
        assertEquals("a photo", read[1].text)
        assertTrue(!read[1].isFavorite)
        assertArrayEquals(media, mediaOut[1])
    }

    @Test
    fun theExportIsEncrypted() {
        val out = ByteArrayOutputStream()
        BackupContainer.write(
            out,
            "a good passphrase".toCharArray(),
            listOf(source(EntryType.TEXT, "a brilliant thing", null))
        )

        val raw = out.toByteArray()
        val asText = String(raw, Charsets.ISO_8859_1)
        assertTrue("the text is in the file", !asText.contains("brilliant thing"))
        assertTrue("the magic is missing", asText.contains("BLX1"))
    }

    @Test
    fun aWrongPassphraseIsRejected() {
        val out = ByteArrayOutputStream()
        BackupContainer.write(out, "the right passphrase".toCharArray(), emptyList())

        val failure = runCatching {
            runBlocking {
                BackupContainer.read(
                    ByteArrayInputStream(out.toByteArray()),
                    "the wrong passphrase".toCharArray()
                ) { _, _ -> }
            }
        }
        assertTrue("a wrong passphrase was accepted", failure.isFailure)
    }

    @Test
    fun theWorkFactorIsStoredWithTheFile() {
        val out = ByteArrayOutputStream()
        BackupContainer.write(out, "a good passphrase".toCharArray(), emptyList())

        // magic (4) + version (1) + iterations (4)
        val raw = out.toByteArray()
        assertEquals(BackupContainer.VERSION, raw[4].toInt())
        val iterations = ((raw[5].toInt() and 0xFF) shl 24) or
            ((raw[6].toInt() and 0xFF) shl 16) or
            ((raw[7].toInt() and 0xFF) shl 8) or
            (raw[8].toInt() and 0xFF)
        assertEquals(BackupContainer.ITERATIONS, iterations)
        // The OWASP password storage cheat sheet recommends 600 000 iterations
        // for PBKDF2-HMAC-SHA256.
        assertTrue("the work factor is too low", BackupContainer.ITERATIONS >= 600_000)
    }

    @Test
    fun aFileThatIsNotAnExportIsRejected() {
        val failure = runCatching {
            runBlocking {
                BackupContainer.read(
                    ByteArrayInputStream("not an export at all".toByteArray()),
                    "a good passphrase".toCharArray()
                ) { _, _ -> }
            }
        }
        assertTrue("a file that is not an export was accepted", failure.isFailure)
    }
}
