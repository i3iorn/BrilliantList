package dev.schrammel.brilliantlist.core_data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Instrumented test: the media pipeline relies on CryptoManager (AndroidKeyStore)
 * and on the SQLCipher encrypted database, so it cannot run as a JVM test.
 */
@RunWith(AndroidJUnit4::class)
class MediaRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var repository: EntryRepository

    @Before
    fun setUp() {
        db = AppDatabase.build(context, ByteArray(32) { 7 })
        repository = EntryRepository(context, db, db.entryDao(), DatabaseKeyManager(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun imageEntryIsEncryptedOnDiskAndDecryptsBack() = runBlocking {
        val payload = ByteArray(64) { it.toByte() }

        val entry = repository.addImageEntry(ByteArrayInputStream(payload))

        assertEquals(EntryType.IMAGE, entry.type)
        assertEquals("media/${entry.id}.enc", entry.mediaUri)

        val file = File(context.filesDir, entry.mediaUri!!)
        assertTrue("encrypted media file is missing", file.exists())

        // The file on disk must be ciphertext, not the plaintext payload.
        val onDisk = file.readBytes()
        assertFalse("media was written unencrypted", onDisk.contentEquals(payload))
        // 12 byte IV prefix + ciphertext + 16 byte GCM tag.
        assertTrue(onDisk.size > payload.size)

        val decrypted = repository.openDecryptedMedia(entry)
        assertNotNull("decryption failed", decrypted)
        assertArrayEquals(payload, decrypted!!.use { it.readBytes() })
    }

    @Test
    fun favoriteFlagRoundTrips() = runBlocking {
        val entry = repository.addTextEntry("favorite me")
        assertFalse(entry.isFavorite)

        repository.setFavorite(entry.id, true)

        // observeAll() only returns active rows, so read the row directly.
        assertTrue(db.entryDao().getById(entry.id)!!.isFavorite)
    }

    @Test
    fun softDeletedEntryCanBeRestored() = runBlocking {
        val entry = repository.addTextEntry("undo me")

        repository.softDelete(entry.id)
        assertTrue(db.entryDao().getById(entry.id)!!.isDeleted)
        // A soft deleted entry disappears from the active list.
        assertTrue(db.entryDao().observeAll().first().none { it.id == entry.id })

        repository.restore(entry)
        assertFalse(db.entryDao().getById(entry.id)!!.isDeleted)
        assertTrue(db.entryDao().observeAll().first().any { it.id == entry.id })
    }
}
