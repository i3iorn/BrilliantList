package dev.schrammel.brilliantlist.core_data.database

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.schrammel.brilliantlist.core_encryption.CryptoManager
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * The key of the database is versioned, so the specification of the key can
 * change without losing the entries.
 *
 * The rotation is the risky part: if it loses the passphrase, the database of
 * every existing install is gone.
 */
@RunWith(AndroidJUnit4::class)
class KeyRotationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val prefs by lazy {
        context.getSharedPreferences("db_keys", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        clear()
    }

    @After
    fun tearDown() {
        clear()
    }

    private fun clear() {
        prefs.edit().clear().apply()
        // The key manager follows the app lock setting through a mirror in
        // these preferences. The tests run without the lock: an auth key
        // would need a user authentication the test runner cannot provide.
        prefs.edit()
            .putBoolean(DatabaseKeyManager.AUTH_BOUND_PREF, false)
            .apply()
        CryptoManager.deleteKey(CryptoManager.aliasFor(1))
        CryptoManager.deleteKey(CryptoManager.aliasFor(2))
    }

    @Test
    fun thePassphraseSurvivesAKeyRotation() {
        val manager = DatabaseKeyManager(context)
        val passphrase = manager.getOrCreatePassphrase()
        assertNotNull(passphrase)
        assertTrue("the passphrase is too short", passphrase.size >= 32)

        val aliasBefore = manager.activeAlias()
        assertNotNull(CryptoManager.getKey(aliasBefore))

        // Replace the key: the data stays readable, the old key is gone.
        manager.rotateKeyToNewAlias()

        val aliasAfter = manager.activeAlias()
        assertFalse("the key was not replaced", aliasBefore == aliasAfter)
        assertNull("the old key is still there", CryptoManager.getKey(aliasBefore))
        assertNotNull("the new key is missing", CryptoManager.getKey(aliasAfter))

        // The passphrase is the same, so the database still opens.
        val second = DatabaseKeyManager(context)
        assertArrayEquals(passphrase, second.getOrCreatePassphrase())
    }

    @Test
    fun aMissingKeyIsReportedInsteadOfCrashing() {
        val manager = DatabaseKeyManager(context)
        manager.getOrCreatePassphrase()
        manager.rotateKeyToNewAlias()

        // Simulate a lost key: the Keystore was reset. The wrap key of the
        // passphrase is not the media key, so it is the wrap key that goes.
        CryptoManager.deleteKey(manager.activeAuthAlias())

        assertTrue("the lost key was not reported", manager.isKeyLost())

        // A manager without a cached passphrase fails instead of opening a
        // database it cannot read any more.
        val failure = runCatching { DatabaseKeyManager(context).getOrCreatePassphrase() }
        assertTrue("a lost key did not fail", failure.isFailure)
    }

    /**
     * The upgrade path of an existing install.
     *
     * The first versions stored the passphrase in the shared preferences
     * without the alias of the key, and the key was created with an older
     * specification. The first launch of the new build has to rotate the key
     * without losing the database.
     */
    @Test
    fun anExistingInstallIsRotatedWithoutLosingTheDatabase() {
        // The state of an install that was created before the keys were
        // versioned: the passphrase is wrapped with the legacy key and the
        // preferences do not know the alias.
        val legacyAlias = CryptoManager.aliasFor(1)
        val legacyKey = CryptoManager.createKey(legacyAlias)
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(legacyKey, ByteArrayInputStream(passphrase), wrapped)
        prefs.edit()
            .putString("sqlcipher_passphrase", Base64.encodeToString(wrapped.toByteArray(), Base64.NO_WRAP))
            .apply()

        // The first launch of the new build.
        val manager = DatabaseKeyManager(context)
        val read = manager.getOrCreatePassphrase()

        assertArrayEquals("the passphrase was lost by the rotation", passphrase, read)
        assertEquals(
            "the key was not rotated",
            CryptoManager.aliasFor(CryptoManager.KEY_VERSION),
            manager.activeAuthAlias()
        )
        assertNotNull(
            "the new key is missing",
            CryptoManager.getKey(CryptoManager.aliasFor(CryptoManager.KEY_VERSION))
        )

        // The next launch reads the same passphrase.
        val second = DatabaseKeyManager(context)
        assertArrayEquals(passphrase, second.getOrCreatePassphrase())
    }
}
