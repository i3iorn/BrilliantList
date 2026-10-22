package dev.schrammel.brilliantlist.core_data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val passphrase = ByteArray(32) { 1 }
        db = AppDatabase.build(ApplicationProvider.getApplicationContext(), passphrase)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun databaseOpens() {
        assertNotNull(db)
    }
}

