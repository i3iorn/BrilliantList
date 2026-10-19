package dev.schrammel.brilliantlist.core_data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import dev.schrammel.brilliantlist.core_model.Entry

@Database(entities = [EntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context, passphrase: ByteArray): AppDatabase {
            // SupportFactory requires the SQLCipher native libs to be loaded first,
            // otherwise the first database open crashes with UnsatisfiedLinkError.
            SQLiteDatabase.loadLibs(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, "brilliantlist.db")
                .openHelperFactory(SupportFactory(passphrase))
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
