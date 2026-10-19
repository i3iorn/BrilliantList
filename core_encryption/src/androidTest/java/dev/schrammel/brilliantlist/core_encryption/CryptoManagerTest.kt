package dev.schrammel.brilliantlist.core_encryption

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Instrumented test: CryptoManager relies on AndroidKeyStore, unavailable to JVM unit tests. */
class CryptoManagerTest {
    @Test
    fun encryptAndDecryptStream() {
        val input = ByteArrayInputStream("test-data".toByteArray())
        val encrypted = ByteArrayOutputStream()
        CryptoManager.encrypt(input, encrypted)
        val decrypted = ByteArrayOutputStream()
        CryptoManager.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted)
        assertEquals("test-data", String(decrypted.toByteArray()))
    }
}
