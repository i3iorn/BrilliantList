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
     * The passphrase is wrapped by one key of the current build: either an
     * auth key that requires the user to authenticate (app lock on), or the
     * master key (app lock off, or a device without a screen lock). Which one
     * it is was decided when the passphrase was written and is stored next to
     * it, so an existing install always keeps its wrap - a build that changes
     * the policy must not silently re-wrap an installed passphrase, it would
     * ask for an authentication the app never performed.
     */
    fun activeAuthAlias(): String =
        prefs.getString(AUTH_KEY_ALIAS, null) ?: activeAlias()

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
     * The alias of the auth key that a *new* passphrase is wrapped with, when
     * the app lock is on: a key that requires the user to prove who they are
     * before the database can be opened.
     */
    private fun defaultAuthAlias(): String =
        CryptoManager.aliasFor(CryptoManager.KEY_VERSION) + "_auth"

    /**
     * Whether a passphrase that is written from now on is wrapped by an auth
     * key.
     *
     * The binding follows the app lock: a user who switches the lock off has
     * opted out of the authentication, so the journal opens without a prompt.
     * The device still has to be unlocked ([CryptoManager.createKey] sets
     * `setUnlockedDeviceRequired`), and a device without a screen lock cannot
     * bind at all. The setting is mirrored into the preferences of this
     * manager ([AUTH_BOUND_PREF]), so the check stays synchronous - the
     * manager is called from the widget and the worker, where a DataStore
     * read would block the caller.
     */
    private fun authWanted(): Boolean =
        canAuthenticate(context) && prefs.getBoolean(AUTH_BOUND_PREF, true)

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
     * Re-wraps the passphrase with a key that does not require user
     * authentication.
     *
     * Called when the user switches the app lock off: a passphrase that is
     * still wrapped by an auth key would ask for an authentication the app
     * never performs (the lock screen is skipped), and the journal could not
     * be opened at all. The plaintext is only available in the cache, so this
     * is a best effort - a passphrase that was written while the lock was on
     * is re-wrapped here the first time the journal was opened.
     */
    fun onAppLockDisabled() {
        val plaintext = cachedPassphrase ?: return
        val stored = prefs.getString(PASSPHRASE_KEY, null) ?: return
        // Only an auth-wrapped passphrase has to change. The alias records
        // the type of the key, so this check needs no unlock.
        if (prefs.getString(AUTH_KEY_ALIAS, null)?.endsWith("_auth") != true) return

        val oldAuthAlias = prefs.getString(AUTH_KEY_ALIAS, null)
        val newAlias = CryptoManager.aliasFor(CryptoManager.KEY_VERSION)
        val newKey = CryptoManager.createKey(newAlias)
        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(newKey, ByteArrayInputStream(plaintext), out)
        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(AUTH_KEY_ALIAS, newAlias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()
        if (oldAuthAlias != null && oldAuthAlias != newAlias) {
            CryptoManager.deleteKey(oldAuthAlias)
        }
    }

    /**
     * Replaces the key with a new one.
     *
     * The media key and the key that wraps the passphrase are both replaced:
     * the passphrase is re-wrapped with the new key, so the database stays
     * readable, and the old keys are deleted. After this the data that the
     * old keys protected cannot be read any more - which is exactly what
     * "erase everything" needs.
     */
    fun rotateKeyToNewAlias() {
        val stored = prefs.getString(PASSPHRASE_KEY, null) ?: return
        val oldWrapAlias = activeAuthAlias()
        val oldWrapKey = CryptoManager.getKey(oldWrapAlias) ?: return

        val plaintext = ByteArrayOutputStream()
        CryptoManager.decryptWithKey(
            oldWrapKey,
            ByteArrayInputStream(Base64.decode(stored, Base64.NO_WRAP)),
            plaintext
        )

        // The media key is replaced as well: old copies of the media files
        // must not stay readable with a key that still exists somewhere.
        val oldMediaAlias = activeAlias()
        val newMediaAlias = CryptoManager.aliasFor(CryptoManager.KEY_VERSION) + "_" +
            System.currentTimeMillis()
        val newMediaKey = CryptoManager.createKey(newMediaAlias)

        val newWrapAlias: String
        val newWrapKey: SecretKey
        if (authWanted()) {
            newWrapAlias = defaultAuthAlias() + "_" + System.currentTimeMillis()
            newWrapKey = CryptoManager.createAuthKey(newWrapAlias)
        } else {
            // Without the app lock the passphrase shares the media key, as it
            // did in the versions before the auth key existed.
            newWrapAlias = newMediaAlias
            newWrapKey = newMediaKey
        }

        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(newWrapKey, ByteArrayInputStream(plaintext.toByteArray()), out)

        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(KEY_ALIAS, newMediaAlias)
            .putString(AUTH_KEY_ALIAS, newWrapAlias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()

        CryptoManager.deleteKey(oldMediaAlias)
        if (oldWrapAlias != oldMediaAlias) {
            CryptoManager.deleteKey(oldWrapAlias)
        }
    }

    /** Creates a key of the current policy and a fresh passphrase. */
    private fun createFreshPassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_SIZE).also { SecureRandom().nextBytes(it) }
        if (authWanted()) {
            val alias = defaultAuthAlias()
            storePassphrase(passphrase, CryptoManager.createAuthKey(alias), alias)
        } else {
            // The passphrase key and the media key are separate: the media
            // key must never be recreated, it would make the existing files
            // unreadable (there is no per-file key version).
            val alias = CryptoManager.aliasFor(CryptoManager.KEY_VERSION)
            storePassphrase(passphrase, CryptoManager.createKey(alias), alias)
        }
        return passphrase
    }

    /**
     * Re-wraps the passphrase with a key of the current specification.
     *
     * The new passphrase is written before the old key is deleted, so an
     * interrupted rotation can never lose the database. The wrap type follows
     * the current policy: an install whose passphrase was wrapped by the
     * plain master key is re-wrapped by a key of the current version under
     * its own alias - the media key must never be recreated here, it would
     * make every photo and recording unreadable (there is no per-file key
     * version). The old wrap key is only deleted when it protects nothing
     * else: on installs that predate the versioning the wrap key is also the
     * media key, and it has to survive.
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

        val newAlias: String
        val newKey: SecretKey
        if (authWanted() && prefs.getString(AUTH_KEY_ALIAS, null) != null) {
            // The passphrase is already wrapped by an auth key of the current
            // policy: keep the binding, only refresh the key itself.
            newAlias = defaultAuthAlias()
            newKey = CryptoManager.createAuthKey(newAlias)
        } else {
            newAlias = CryptoManager.aliasFor(CryptoManager.KEY_VERSION)
            newKey = CryptoManager.createKey(newAlias)
        }
        val out = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(newKey, ByteArrayInputStream(passphrase), out)

        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .putString(AUTH_KEY_ALIAS, newAlias)
            .putInt(KEY_VERSION, CryptoManager.KEY_VERSION)
            .apply()

        // The media key must survive: it still protects the existing files.
        if (oldAlias != newAlias && oldAlias != activeAlias()) {
            CryptoManager.deleteKey(oldAlias)
        }
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

    companion object {
        /** The SharedPreferences file that holds the wrapped database passphrase. */
        const val PREFS_NAME = "db_keys"
        private const val PASSPHRASE_KEY = "sqlcipher_passphrase"
        private const val KEY_VERSION = "key_version"
        private const val KEY_ALIAS = "key_alias"
        private const val AUTH_KEY_ALIAS = "auth_key_alias"
        private const val PASSPHRASE_SIZE = 32

        /**
         * The mirror of the app lock setting. Written by
         * [dev.schrammel.brilliantlist.core_data.repository.SettingsRepository]
         * whenever the setting changes; [DatabaseKeyManager.authWanted] reads
         * it synchronously. Tests set it to false, so the passphrase is
         * wrapped by the master key and no user authentication is needed.
         */
        const val AUTH_BOUND_PREF = "auth_bound"

        /** The version of the key that was used before the keys were versioned. */
        const val LEGACY_VERSION = 1
    }
}
