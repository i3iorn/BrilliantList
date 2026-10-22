package dev.schrammel.brilliantlist.core_encryption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Instrumented test: CryptoManager relies on AndroidKeyStore, unavailable to JVM unit tests. */
class CryptoManagerTest {

    @Test
    fun encryptAndDecryptStream() {
        val key = CryptoManager.createKey("crypto_test_key")
        try {
            val input = ByteArrayInputStream("test-data".toByteArray())
            val encrypted = ByteArrayOutputStream()
            CryptoManager.encryptWithKey(key, input, encrypted)
            val decrypted = ByteArrayOutputStream()
            CryptoManager.decryptWithKey(key, ByteArrayInputStream(encrypted.toByteArray()), decrypted)
            assertEquals("test-data", String(decrypted.toByteArray()))
        } finally {
            CryptoManager.deleteKey("crypto_test_key")
        }
    }

    @Test
    fun everyEncryptionUsesANewIv() {
        val key = CryptoManager.createKey("crypto_test_iv")
        try {
            val first = ByteArrayOutputStream()
            CryptoManager.encryptWithKey(key, ByteArrayInputStream("same".toByteArray()), first)
            val second = ByteArrayOutputStream()
            CryptoManager.encryptWithKey(key, ByteArrayInputStream("same".toByteArray()), second)
            // Same plaintext, different ciphertext: the IV is never reused.
            assertNotEquals(first.toByteArray().toList(), second.toByteArray().toList())
        } finally {
            CryptoManager.deleteKey("crypto_test_iv")
        }
    }

    @Test
    fun aDeletedKeyCannotDecrypt() {
        val key = CryptoManager.createKey("crypto_test_gone")
        val encrypted = ByteArrayOutputStream()
        CryptoManager.encryptWithKey(key, ByteArrayInputStream("secret".toByteArray()), encrypted)
        CryptoManager.deleteKey("crypto_test_gone")

        assertNull(CryptoManager.getKey("crypto_test_gone"))
        // The key is gone, so the data is unreadable. The application has to
        // fail closed instead of silently returning garbage.
        val failure = runCatching {
            val keyAgain = CryptoManager.getKey("crypto_test_gone")
                ?: error("no key")
            val out = ByteArrayOutputStream()
            CryptoManager.decryptWithKey(keyAgain, ByteArrayInputStream(encrypted.toByteArray()), out)
        }
        assertEquals(true, failure.isFailure)
    }

    @Test
    fun versionedAliasesAreStable() {
        // Version 1 is the alias the first installs have; changing it would
        // make their key unreachable.
        assertEquals("brilliantlist_master_key", CryptoManager.aliasFor(1))
        assertNotEquals(CryptoManager.aliasFor(1), CryptoManager.aliasFor(2))
    }
}
