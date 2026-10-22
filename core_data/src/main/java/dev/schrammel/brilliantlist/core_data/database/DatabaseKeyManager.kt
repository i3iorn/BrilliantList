package dev.schrammel.brilliantlist.core_data.database

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import android.util.Base64
import dev.schrammel.brilliantlist.core_encryption.CryptoManager
import dev.schrammel.brilliantlist.core_encryption.CryptoUnavailableException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * Provides the SQLCipher passphrase. A random passphrase is generated once,
 * encrypted with [CryptoManager] (Android Keystore AES-GCM, IV prepended) and
 * only the ciphertext is persisted in shared preferences. The Keystore key
 * itself is non-exportable, so the passphrase can never be read off-device.
 *
 * The key is versioned: the version of the key the passphrase is wrapped with
 * is stored next to it. When the key specification changes, the passphrase is
 * re-wrapped with a key of the new version (see [rotateKey]) so the database
 * stays readable.
 *
 * ## User authentication
 *
 * The "master key" (version 2) encrypts the media files. The passphrase of
 * the database is wrapped by an "auth key" (version 3) that requires the user
 * to authenticate with a biometric or the device credential. The database
 * therefore cannot be opened before the user unlocks it. On devices without a
 * screen lock the app falls back to the master key, so a fresh install is
 * still usable.
 */
class DatabaseKeyManager @Inject constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The passphrase of this session.
     *
     * The Keystore is slow - the first call has to create the key, which can
     * take a moment. Caching the passphrase means that the work happens once
     * and not on every call.
     */
    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * The version of the key the stored passphrase is wrapped with.
     *
     * Installs that predate the versioning used the unversioned alias, which
     * is version [LEGACY_VERSION].
     */
    private val storedVersion: Int
        get() = prefs.getInt(KEY_VERSION, LEGACY_VERSION)

    /**
     * The alias of the key that currently protects the media files.
     *
     * The alias is stored, so the key can be replaced without losing the
     * data it protects (see [rotateKeyToNewAlias]).
     */
    fun activeAlias(): String =
        prefs.getString(KEY_ALIAS, null) ?: CryptoManager.aliasFor(LEGACY_VERSION)

    /**
     * The alias of the key that protects the database passphrase.
     *
     * When the device can authenticate, this is a key that requires user
     * authentication. Otherwise it falls back to the master key, so a device
     * without a screen lock still works.
     */
    fun activeAuthAlias(): String {
        if (canAuthenticate(context)) {
            return prefs.getString(AUTH_KEY_ALIAS, null)
                ?: (CryptoManager.aliasFor(CryptoManager.KEY_VERSION) + "_auth")
        }
        return activeAlias()
    }

    /**
     * The key that protects the database passphrase.
     *
     * This key requires user authentication when the device supports it.
     */
    fun activeAuthKey(): SecretKey {
        val alias = activeAuthAlias()
        return CryptoManager.getKey(alias)
            ?: if (canAuthenticate(context)) {
                CryptoManager.createAuthKey(alias)
            } else {
                CryptoManager.createKey(alias)
            }
    }

    /**
     * True when the device can ask for a biometric or the device credential.
     */
    private fun canAuthenticate(context: Context): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(context).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Creates the key and the passphrase in the background.
     *
     * The first call of the Keystore can take a moment. Doing it while the
     * app starts keeps the first frame smooth; a failure is ignored, the
     * passphrase is then created when it is really needed.
     */
    fun warmUp() {
        // Only warm up the master key (for media). The passphrase needs the
        // auth key, which would prompt the user before they unlocked the app.
        runCatching { CryptoManager.createKey(activeAlias()) }
    }

    /**
     * The key that protects the media files.
     *
     * Media is encrypted with the same master key as the passphrase, so the
     * key has to be the active one.
     */
    fun activeKey(): SecretKey {
        val alias = activeAlias()
        return CryptoManager.getKey(alias) ?: CryptoManager.createKey(alias)
    }

    /**
     * The SQLCipher passphrase, creating it (and its key) on first use.
     *
     * Fails when the key of an existing passphrase is gone: the database
     * cannot be decrypted anymore and the caller has to offer a way out
     * (see [isKeyLost]).
     *
     * The method is synchronized: the key must not be created twice, the
     * second key would invalidate the passphrase of the first one.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        cachedPassphrase?.let { return it }
        val passphrase = computePassphrase()
        cachedPassphrase = passphrase
        return passphrase
    }

    private fun computePassphrase(): ByteArray {
        val stored = prefs.getString(PASSPHRASE_KEY, null)
        if (stored == null) {
            return createFreshPassphrase()
        }
        if (storedVersion != CryptoManager.KEY_VERSION) {
            return rotateKey(stored)
        }
        val key = CryptoManager.getKey(activeAuthAlias())
            ?: throw CryptoUnavailableException("The encryption key is no longer available")
        val out = ByteArrayOutputStream()
        CryptoManager.decryptWithKey(
            key,
            ByteArrayInputStream(Base64.decode(stored, Base64.NO_WRAP)),
            out
        )
        return out.toByteArray()
    }

    /**
     * True when the database can be opened right now.
     *
     * The master key requires an unlocked device, so this is false while the
     * device is locked (and before the first unlock after a reboot). Callers
     * must fail closed instead of trying to open the database anyway.
     */
    fun isAvailable(): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceLocked == true) return false
        // A key that does not exist yet counts as usable: there is nothing to
        // unlock, it is created together with the database.
        if (prefs.getString(PASSPHRASE_KEY, null) == null) return true
        // The auth key requires user authentication. The key is unusable
        // while the device is locked.
        return CryptoManager.isKeyUsable(activeAuthAlias())
    }

    /**
     * True when a passphrase exists but its key is gone.
     *
     * The entries cannot be decrypted anymore (the Keystore was reset or the
     * key was invalidated). The user has to be told and has to be able to
     * start over.
     */
    fun isKeyLost(): Boolean {
        if (prefs.getString(PASSPHRASE_KEY, null) == null) return false
        return CryptoManager.getKey(activeAuthAlias()) == null
    }

    /**
     * Removes the passphrase and the key.
     *
     * The database and the media files are deleted by the caller, this only
     * takes care of the key material.
     */
    fun wipe() {
        prefs.edit()
            .remove(PASSPHRASE_KEY)
            .remove(KEY_VERSION)
            .remove(KEY_ALIAS)
            .remove(AUTH_KEY_ALIAS)
            .apply()
        cachedPassphrase = null
        CryptoManager.deleteKey(activeAlias())
        CryptoManager.deleteKey(activeAuthAlias())
        // A key of a previous version can still be around when the rotation
        // was interrupted; it protects nothing anymore, so it goes too.
        for (version in 1..CryptoManager.KEY_VERSION) {
            CryptoManager.deleteKey(CryptoManager.aliasFor(version))
        }
    }

    /**
     * Replaces the key with a new one.
     *
     * The passphrase is re-wrapped with the new key, so the database stays
     * readable, and the old key is deleted. After this the data that the old
     * key protected cannot be read any more - which is exactly what
     * "erase everything" needs.
     */
    fun rotateKeyToNewAlias() {
        val stored = prefs.getString(PASSPHRASE_KEY, null) ?: return
        val oldAlias = activeAuthAlias()
        val oldKey = CryptoManager.getKey(oldAlias) ?: return

        val plaintext = ByteArrayOutputStream()
        CryptoManager.decryptWithKey(
            oldKey,
            ByteArrayInputStream(Base64.decode(stored, Base64.NO_WRAP)),
            plaintext
        )

        val newAlias = CryptoManager.aliasFor(CryptoManager.KEY_VERSION) + "_" +
            System.currentTimeMillis()
        val newKey = if (canAuthenticate(context)) {
            CryptoManager.createAuthKey(newAlias)
        } else {
            CryptoManager.createKey(newAlias)
        }
        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(newKey, ByteArrayInputStream(plaintext.toByteArray()), out)

        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(AUTH_KEY_ALIAS, newAlias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()

        CryptoManager.deleteKey(oldAlias)
    }

    /** Creates a key of the current version and a fresh passphrase. */
    private fun createFreshPassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_SIZE).also { SecureRandom().nextBytes(it) }
        val alias = activeAuthAlias()
        val key = activeAuthKey()
        storePassphrase(passphrase, key, alias)
        return passphrase
    }

    /**
     * Re-wraps the passphrase with a key of the current version.
     *
     * The new passphrase is written before the old key is deleted, so an
     * interrupted rotation can never lose the database.
     */
    private fun rotateKey(stored: String): ByteArray {
        val oldAlias = activeAuthAlias()
        val oldKey = CryptoManager.getKey(oldAlias)
            ?: throw CryptoUnavailableException("The encryption key is no longer available")

        val plaintext = ByteArrayOutputStream()
        CryptoManager.decryptWithKey(
            oldKey,
            ByteArrayInputStream(Base64.decode(stored, Base64.NO_WRAP)),
            plaintext
        )
        val passphrase = plaintext.toByteArray()

        val newAlias = activeAuthAlias()
        val newKey = activeAuthKey()
        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(newKey, ByteArrayInputStream(passphrase), out)

        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(AUTH_KEY_ALIAS, newAlias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()

        CryptoManager.deleteKey(oldAlias)
        return passphrase
    }

    private fun storePassphrase(passphrase: ByteArray, key: SecretKey, alias: String) {
        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(key, ByteArrayInputStream(passphrase), out)
        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(AUTH_KEY_ALIAS, alias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "db_keys"
        const val PASSPHRASE_KEY = "sqlcipher_passphrase"
        const val KEY_VERSION = "key_version"
        const val KEY_ALIAS = "key_alias"
        const val AUTH_KEY_ALIAS = "auth_key_alias"
        const val PASSPHRASE_SIZE = 32

        /** The version of the key that was used before the keys were versioned. */
        const val LEGACY_VERSION = 1
    }
}
