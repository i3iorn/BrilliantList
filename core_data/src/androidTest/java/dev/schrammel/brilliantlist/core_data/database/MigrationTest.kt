package dev.schrammel.brilliantlist.core_data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The version 1 schema, used to create a database that has to be migrated.
 *
 * Only the entries table existed in version 1; the app state table was added
 * in version 2.
 */
@Entity(tableName = "entries")
data class EntryEntityV1(
    @PrimaryKey val id: UUID,
    val type: String,
    val textContent: String? = null,
    val mediaUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val position: Int,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false
)

@Dao
interface EntryDaoV1 {
    @Query("SELECT * FROM entries")
    suspend fun all(): List<EntryEntityV1>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntityV1)
}

@Database(entities = [EntryEntityV1::class], version = 1, exportSchema = false)
abstract class AppDatabaseV1 : RoomDatabase() {
    abstract fun entryDao(): EntryDaoV1
}

/**
 * Instrumented test: the migration runs against a real (encrypted) database.
 * A broken migration would destroy the entries of every existing install.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration_test.db"
    private val passphrase = ByteArray(32) { 5 }

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun version1DatabaseIsMigratedWithoutLosingEntries() = runBlocking {
        // Create a database with the version 1 schema, encrypted like the real one.
        val legacy = Room
            .databaseBuilder(context, AppDatabaseV1::class.java, databaseName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        val entryId = UUID.randomUUID()
        legacy.entryDao().insert(
            EntryEntityV1(
                id = entryId,
                type = "TEXT",
                textContent = "written before the migration",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                position = 0
            )
        )
        legacy.close()

        // Opening it with the current schema has to run the migration.
        val migrated = AppDatabase.build(context, passphrase, databaseName)
        val entries = migrated.entryDao().observeAll().first()
        assertEquals(1, entries.size)
        assertEquals("written before the migration", entries.first().textContent)
        assertEquals(entryId, entries.first().id)

        // The new table exists and is usable.
        migrated.appStateDao().upsert(AppStateEntity(key = "k", value = "v"))
        assertEquals("v", migrated.appStateDao().get("k"))

        migrated.close()
    }
}
