package dev.schrammel.brilliantlist.feature_entry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Instrumented test: the media path needs CryptoManager (AndroidKeyStore) and
 * the SQLCipher database, so it cannot run as a JVM test.
 */
@RunWith(AndroidJUnit4::class)
class MediaLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var repository: EntryRepository

    @Before
    fun setUp() {
        db = AppDatabase.build(context, ByteArray(32) { 3 })
        repository = EntryRepository(context, db, db.entryDao(), DatabaseKeyManager(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun decodesEncryptedImageThumbnail() = runBlocking {
        val source = Bitmap.createBitmap(SOURCE_WIDTH, SOURCE_HEIGHT, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream().use { out ->
            source.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        val entry = repository.addImageEntry(ByteArrayInputStream(bytes))
        val thumbnail = MediaLoader(repository).thumbnail(entry, TARGET_WIDTH_PX)

        assertNotNull("encrypted image could not be decoded", thumbnail)
        assertTrue(
            "thumbnail was not downscaled",
            thumbnail!!.width <= SOURCE_WIDTH
        )
        assertEquals("downscaled to the wrong size", TARGET_WIDTH_PX, thumbnail.width)
    }

    @Test
    fun missingMediaDecodesToNull() = runBlocking {
        val entry = repository.addImageEntry(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        // Replace the file with garbage: decoding must fail gracefully.
        java.io.File(context.filesDir, entry.mediaUri!!).writeBytes(byteArrayOf(9, 9, 9))

        assertEquals(null, MediaLoader(repository).thumbnail(entry, TARGET_WIDTH_PX))
    }

    private companion object {
        const val SOURCE_WIDTH = 64
        const val SOURCE_HEIGHT = 48
        const val TARGET_WIDTH_PX = 32
    }
}
