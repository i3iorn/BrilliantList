package dev.schrammel.brilliantlist.core_encryption

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG
import android.security.keystore.KeyProperties.AUTH_DEVICE_CREDENTIAL
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption on top of the Android Keystore.
 *
 * The master key is non-exportable: it never leaves the Keystore and is never
 * written to disk. Everything that has to be protected (the SQLCipher
 * passphrase, the media files) is encrypted with it.
 *
 * The key is versioned through its alias (see [aliasFor]): a change to the key
 * specification - for example adding `setUserAuthenticationRequired` - means a
 * new key, and the old one can only be deleted after the data it protected has
 * been re-wrapped. [DatabaseKeyManager] owns that rotation.
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "brilliantlist_master_key"
    private const val AES_MODE = "AES/GCM/NoPadding"

    /** The size of the IV, in bytes. */
    const val IV_SIZE = 12

    /** The size of the GCM tag, in bits - that is what the cipher expects. */
    private const val TAG_SIZE = 128

    /** The size of the GCM tag, in bytes. */
    const val TAG_SIZE_BYTES = 16

    /**
     * The number of bytes the encryption adds to a file.
     *
     * The ciphertext has the same length as the plaintext, the IV and the tag
     * are added. It is used to know the size of the plaintext of an encrypted
     * file without decrypting it.
     */
    const val OVERHEAD = IV_SIZE + TAG_SIZE_BYTES

    /**
     * The version of the key specification.
     *
     * Bump it whenever [createKey] changes in a way that makes the old key
     * unusable. The data protected by the old key is then re-wrapped with a
     * new key (see the rotation in DatabaseKeyManager).
     */
    const val KEY_VERSION = 2

    /**
     * The alias of the key of the given [version].
     *
     * Version 1 used the unversioned alias - that is the key the first
     * installs have - so it is kept as it is. Later versions get a suffix.
     */
    fun aliasFor(version: Int): String = when (version) {
        1 -> KEY_ALIAS_PREFIX
        else -> "$KEY_ALIAS_PREFIX" + "_v" + version
    }

    /**
     * Creates a key with the current specification under [alias].
     *
     * An existing key with that alias is replaced, so the alias must not be
     * in use yet.
     */
    fun createKey(alias: String): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // CryptoManager provides its own IV via GCMParameterSpec; Android Keystore
                // rejects externally supplied IVs unless randomized encryption is disabled.
                .setRandomizedEncryptionRequired(false)
                .setUserAuthenticationRequired(false)
                // The key must not be usable while the device is locked: journal content
                // must stay unreadable on a locked device (and before the first unlock
                // after a reboot).
                .setUnlockedDeviceRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (e: CryptoUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw CryptoUnavailableException("The device keystore is not available", e)
        }
    }

    /**
     * Creates a key that requires user authentication (biometric or device
     * credential) before it can be used.
     *
     * The key wraps the SQLCipher passphrase, so the database cannot be
     * opened without the user proving who they are. The key is only usable
     * while the device is unlocked.
     *
     * This key is version 3. The "master key" (version 2) still protects the
     * media files. The auth key wraps the passphrase, which is the "master
     * key" of the database. This satisfies the requirement to bind the
     * Keystore key to the user without breaking the existing media pipeline.
     */
    fun createAuthKey(alias: String): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .setUserAuthenticationRequired(true)
                .setUnlockedDeviceRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(0)
            }

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (e: CryptoUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw CryptoUnavailableException("The device keystore is not available", e)
        }
    }

    /** The key stored under [alias], or null when there is none. */
    fun getKey(alias: String): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(alias, null) as? SecretKey
    }.getOrNull()

    /** Removes the key stored under [alias], if there is one. */
    fun deleteKey(alias: String) {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    /**
     * True when the key under [alias] can be used right now.
     *
     * With [KeyGenParameterSpec.setUnlockedDeviceRequired] the key is unusable
     * while the device is locked and before the first unlock after a reboot.
     * The probe initialises a cipher, which is the cheapest reliable check.
     *
     * A key that does not exist yet counts as usable: it is created together
     * with the database, and the caller checks the device lock separately.
     */
    fun isKeyUsable(alias: String): Boolean = runCatching {
        val key = getKey(alias) ?: return true
        val cipher = Cipher.getInstance(AES_MODE)
        // The IV is irrelevant for the probe, nothing is ever encrypted with it.
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, ByteArray(IV_SIZE)))
        true
    }.getOrDefault(false)

    /** Encrypts [input] into [output] with [key]. The output starts with the IV. */
    fun encryptWithKey(key: SecretKey, input: InputStream, output: OutputStream) {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        output.write(iv)
        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut)
        }
    }

    /**
     * Opens an encrypting stream on top of [output].
     *
     * The IV is written first, the rest of the stream is the ciphertext. The
     * caller has to close the returned stream: closing it writes the GCM tag,
     * without it the data cannot be decrypted.
     *
     * This is what makes it possible to record straight into the encrypted
     * file: the plaintext never reaches the disk.
     */
    fun encryptToStream(key: SecretKey, output: OutputStream): OutputStream {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        output.write(iv)
        return CipherOutputStream(output, cipher)
    }

    /**
     * Streaming decryption for on-demand media reads: reads the IV prefix, then wraps
     * the remainder of the input in a CipherInputStream. Caller must close the stream.
     */
    fun decryptToStreamWithKey(key: SecretKey, input: InputStream): InputStream {
        val iv = readIv(input)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        return CipherInputStream(input, cipher)
    }

    /** Decrypts [input] into [output] with [key]. */
    fun decryptWithKey(key: SecretKey, input: InputStream, output: OutputStream) {
        val iv = readIv(input)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output)
        }
    }

    /**
     * Reads the IV prefix.
     *
     * A single read() may return fewer bytes than requested (partial read);
     * loop until the full IV prefix is consumed or the stream ends.
     */
    private fun readIv(input: InputStream): ByteArray {
        val iv = ByteArray(IV_SIZE)
        var read = 0
        while (read < IV_SIZE) {
            val r = input.read(iv, read, IV_SIZE - read)
            if (r == -1) throw IllegalArgumentException("Invalid IV: stream too short")
            read += r
        }
        return iv
    }
}
