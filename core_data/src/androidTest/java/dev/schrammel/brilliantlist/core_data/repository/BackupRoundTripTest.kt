package dev.schrammel.brilliantlist.core_data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.schrammel.brilliantlist.core_data.database.AppDatabase
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import kotlinx.coroutines.flow.first
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
 * The backup needs the encrypted database and the Keystore, so it runs on a
 * device.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var repository: EntryRepository

    @Before
    fun setUp() {
        db = AppDatabase.build(context, ByteArray(32) { 11 }, "backup_test.db")
        repository = EntryRepository(context, db, db.entryDao(), DatabaseKeyManager(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("backup_test.db")
    }

    @Test
    fun exportedEntriesCanBeImportedAgain() = runBlocking {
        repository.addTextEntry("the first brilliant thing")
        repository.addTextEntry("the second brilliant thing")
        val image = repository.addImageEntry(ByteArrayInputStream(ByteArray(64) { it.toByte() }))

        val export = ByteArrayOutputStream()
        val count = repository.exportTo(export, "a good passphrase".toCharArray())
        assertEquals(3, count)

        // The export is encrypted: the text must not be in the file.
        val raw = export.toByteArray()
        assertTrue(
            "the export contains plaintext",
            !String(raw, Charsets.ISO_8859_1).contains("brilliant thing")
        )

        repository.deleteAll()
        assertTrue(repository.observeEntries().first().isEmpty())

        val imported = repository.importFrom(
            ByteArrayInputStream(raw),
            "a good passphrase".toCharArray()
        )
        assertEquals(3, imported)

        val entries = repository.observeEntries().first()
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.textContent == "the first brilliant thing" })
        assertTrue(entries.any { it.textContent == "the second brilliant thing" })

        // The media survived the round trip and is still readable.
        val restored = entries.first { it.type == dev.schrammel.brilliantlist.core_model.EntryType.IMAGE }
        val media = repository.openDecryptedMedia(restored)
        assertNotNull("the media is gone", media)
        assertEquals(64, media!!.use { it.readBytes().size })
        assertTrue("the media of the original entry is missing", image.id != restored.id)
    }

    @Test
    fun aWrongPassphraseIsRejected() = runBlocking {
        repository.addTextEntry("something secret")

        val export = ByteArrayOutputStream()
        repository.exportTo(export, "the right passphrase".toCharArray())

        val failure = runCatching {
            repository.importFrom(
                ByteArrayInputStream(export.toByteArray()),
                "the wrong passphrase".toCharArray()
            )
        }
        assertTrue("a wrong passphrase was accepted", failure.isFailure)
    }
}
