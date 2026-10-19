package dev.schrammel.brilliantlist.core_data.database

import android.content.Context
import android.util.Base64
import dev.schrammel.brilliantlist.core_encryption.CryptoManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Provides the SQLCipher passphrase. A random passphrase is generated once,
 * encrypted with [CryptoManager] (Android Keystore AES-GCM, IV prepended) and
 * only the ciphertext is persisted in shared preferences. The Keystore key
 * itself is non-exportable, so the passphrase can never be read off-device.
 */
class DatabaseKeyManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        prefs.getString(PASSPHRASE_KEY, null)?.let { stored ->
            val out = ByteArrayOutputStream()
            CryptoManager.decrypt(ByteArrayInputStream(Base64.decode(stored, Base64.NO_WRAP)), out)
            return out.toByteArray()
        }
        val passphrase = ByteArray(PASSPHRASE_SIZE).also { SecureRandom().nextBytes(it) }
        val out = ByteArrayOutputStream()
        CryptoManager.encrypt(ByteArrayInputStream(passphrase), out)
        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private companion object {
        const val PREFS_NAME = "db_keys"
        const val PASSPHRASE_KEY = "sqlcipher_passphrase"
        const val PASSPHRASE_SIZE = 32
    }
}
