package dev.schrammel.brilliantlist.core_encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "brilliantlist_master_key"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // CryptoManager provides its own IV via GCMParameterSpec; Android Keystore
            // rejects externally supplied IVs unless randomized encryption is disabled.
            .setRandomizedEncryptionRequired(false)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(input: InputStream, output: OutputStream) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        output.write(iv)
        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut)
        }
    }

    /**
     * Streaming decryption for on-demand media reads: reads the IV prefix, then wraps
     * the remainder of the input in a CipherInputStream. Caller must close the stream.
     */
    fun decryptToStream(input: InputStream): InputStream {
        val iv = ByteArray(IV_SIZE)
        var read = 0
        while (read < IV_SIZE) {
            val r = input.read(iv, read, IV_SIZE - read)
            if (r == -1) throw IllegalArgumentException("Invalid IV: stream too short")
            read += r
        }
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        return CipherInputStream(input, cipher)
    }

    fun decrypt(input: InputStream, output: OutputStream) {
        val secretKey = getOrCreateSecretKey()
        val iv = ByteArray(IV_SIZE)
        if (input.read(iv) != IV_SIZE) throw IllegalArgumentException("Invalid IV")
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output)
        }
    }
}

