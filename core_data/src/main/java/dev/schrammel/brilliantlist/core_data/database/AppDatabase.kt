package dev.schrammel.brilliantlist.core_data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.schrammel.brilliantlist.core_model.Entry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [EntryEntity::class, AppStateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        /**
         * 1 -> 2: adds the app_state table.
         *
         * The database is encrypted, so a migration has to preserve the
         * ciphertext - Room handles that, the passphrase never changes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_state` " +
                        "(`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`))"
                )
            }
        }

        fun build(
            context: Context,
            passphrase: ByteArray,
            name: String = "brilliantlist.db"
        ): AppDatabase {
            // SQLCipher 4.6+ bundles the native library in the AAR, but the app
            // has to load it explicitly before any native method is called.
            // Without this the first database open crashes with
            // UnsatisfiedLinkError.
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
