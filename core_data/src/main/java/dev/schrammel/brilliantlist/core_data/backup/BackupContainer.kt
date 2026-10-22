package dev.schrammel.brilliantlist.core_data.backup

import dev.schrammel.brilliantlist.core_model.EntryType
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The container format of an exported journal.
 *
 * The export is encrypted with a passphrase the user chooses, not with the
 * device key: the file has to be readable on another device, and the user
 * has to be able to get their entries back when this device is gone.
 *
 * Layout:
 * ```
 * magic   "BLX1"          (4 bytes)
 * version 2                (1 byte)
 * iterations               (4 bytes, the work factor of the key derivation)
 * salt                     (16 bytes, for the passphrase derivation)
 * iv                       (12 bytes, for AES-GCM)
 * ciphertext               (the payload, see below)
 * ```
 * The payload holds the entries: type, timestamps, position, favorite flag,
 * the text and the media. The media is stored in plaintext inside the
 * container - the container itself is encrypted, so the file on disk never
 * contains readable content.
 *
 * The container is streamed: the entries are written and read one after the
 * other, so neither an export nor an import needs the whole journal in memory.
 */
object BackupContainer {

    private val MAGIC = "BLX1".toByteArray(Charsets.US_ASCII)

    /** The current version of the format. */
    const val VERSION = 2

    /** The version of the first format, which did not store the work factor. */
    private const val VERSION_1 = 1

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128
    private const val KEY_SIZE_BITS = 256

    /**
     * Iterations of the passphrase derivation.
     *
     * The OWASP password storage cheat sheet recommends 600 000 iterations
     * for PBKDF2-HMAC-SHA256. Deriving the key takes about half a second on
     * a phone, which is fine for an export that happens once in a while.
     */
    const val ITERATIONS = 600_000

    /** The work factor of the first version of the format. */
    private const val LEGACY_ITERATIONS = 120_000

    private const val AES_MODE = "AES/GCM/NoPadding"

    /** Upper bound for a single text or media chunk (32 MB). */
    private const val MAX_PAYLOAD = 32 * 1024 * 1024

    /** Upper bound for the number of entries in one export. */
    private const val MAX_ENTRIES = 100_000

    /**
     * An entry that is written into an export.
     *
     * The media is not held in memory: [mediaSize] says how many bytes it
     * has and [media] opens it when it is written.
     */
    class EntrySource(
        val type: EntryType,
        val text: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val position: Int,
        val isFavorite: Boolean,
        /** The size of the media in bytes, 0 when there is none. */
        val mediaSize: Int,
        /** Opens the media for reading, or null when there is none. */
        val media: () -> InputStream?
    )

    /**
     * An entry that was read from an export.
     *
     * [media] returns a stream with the media of the entry. The stream has
     * to be read (or closed) before the next entry is read.
     */
    class Entry(
        val type: EntryType,
        val text: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val position: Int,
        val isFavorite: Boolean,
        val mediaSize: Int
    )

    /** Writes the entries into [output]. The stream is not closed. */
    fun write(output: OutputStream, passphrase: CharArray, entries: List<EntrySource>) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, ITERATIONS)

        output.write(MAGIC)
        output.write(VERSION)
        output.writeInt(ITERATIONS)
        output.write(salt)
        output.write(iv)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        CipherOutputStream(output, cipher).use { cipherOut ->
            cipherOut.writeInt(entries.size)
            entries.forEach { entry ->
                cipherOut.writeByte(entry.type.ordinal)
                cipherOut.writeLong(entry.createdAt)
                cipherOut.writeLong(entry.updatedAt)
                cipherOut.writeInt(entry.position)
                cipherOut.writeByte(if (entry.isFavorite) 1 else 0)
                val text = entry.text?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                cipherOut.writeInt(text.size)
                cipherOut.write(text)
                cipherOut.writeInt(entry.mediaSize)
                entry.media()?.use { media -> media.copyTo(cipherOut) }
            }
        }
        passphrase.fill('\u0000')
    }

    /**
     * Reads the entries of an export, one after the other.
     *
     * The callback gets the entry and the stream with its media. The stream
     * has to be read or closed before the callback returns, otherwise the
     * next entry cannot be read.
     *
     * @return the number of entries.
     */
    suspend fun read(
        input: InputStream,
        passphrase: CharArray,
        onEntry: suspend (Entry, InputStream?) -> Unit
    ): Int {
        val magic = ByteArray(MAGIC.size)
        if (!input.readFully(magic) || !magic.contentEquals(MAGIC)) {
            throw BackupException("This file is not a BrilliantList export")
        }
        val version = input.read()
        if (version != VERSION && version != VERSION_1) {
            throw BackupException("This export was written by a newer version of the app")
        }
        // The first version did not store the work factor.
        val iterations = if (version == VERSION_1) {
            LEGACY_ITERATIONS
        } else {
            input.readInt().also {
                if (it < 1) throw BackupException("The export file is damaged")
            }
        }
        val salt = ByteArray(SALT_SIZE)
        if (!input.readFully(salt)) throw BackupException("The export file is damaged")
        val iv = ByteArray(IV_SIZE)
        if (!input.readFully(iv)) throw BackupException("The export file is damaged")

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(AES_MODE)
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            CipherInputStream(input, cipher).use { stream ->
                val count = stream.readInt()
                if (count < 0 || count > MAX_ENTRIES) {
                    throw BackupException("The export file is damaged")
                }
                repeat(count) {
                    val type = EntryType.entries.getOrElse(stream.readByte()) { EntryType.TEXT }
                    val createdAt = stream.readLong()
                    val updatedAt = stream.readLong()
                    val position = stream.readInt()
                    val favorite = stream.readByte() != 0
                    val text = stream.readSizedBlock().toString(Charsets.UTF_8).ifEmpty { null }
                    val mediaSize = stream.readInt()
                    if (mediaSize < 0 || mediaSize > MAX_PAYLOAD) {
                        throw BackupException("The export file is damaged")
                    }
                    val media = if (mediaSize > 0) BoundedInputStream(stream, mediaSize) else null
                    onEntry(Entry(type, text, createdAt, updatedAt, position, favorite, mediaSize), media)
                    // The media has to be consumed, otherwise the next entry
                    // cannot be read.
                    media?.skipAll()
                }
                count
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // A wrong passphrase fails the GCM tag check.
            throw BackupException("The passphrase is wrong or the file is damaged", e)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Derives the AES key from the passphrase.
     *
     * PBKDF2 with a random salt, so the same passphrase produces a different
     * key for every export.
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_SIZE_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            // The key has to be an AES key: the factory returns a PBE key,
            // which the cipher refuses.
            val derived = factory.generateSecret(spec).encoded
            return SecretKeySpec(derived, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

/** Thrown when an export cannot be read. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A stream that reads at most [limit] bytes.
 *
 * It is used for the media of an entry: the reader has to know where the
 * media ends, and the container is a single stream.
 */
private class BoundedInputStream(
    private val source: InputStream,
    private val limit: Int
) : InputStream() {

    private var read = 0

    override fun read(): Int {
        if (read >= limit) return -1
        val value = source.read()
        if (value == -1) return -1
        read++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (read >= limit) return -1
        val count = source.read(b, off, minOf(len, limit - read))
        if (count <= 0) return -1
        read += count
        return count
    }

    override fun available(): Int = minOf(source.available(), limit - read)
}

/** Reads the rest of the stream. */
private fun InputStream.skipAll() {
    val buffer = ByteArray(8192)
    while (read(buffer) > 0) {
        // Nothing to do, the bytes are dropped.
    }
}

/** Reads exactly [count] bytes, or fails. */
private fun InputStream.readFully(target: ByteArray): Boolean {
    var read = 0
    while (read < target.size) {
        val r = read(target, read, target.size - read)
        if (r == -1) return false
        read += r
    }
    return true
}

private fun InputStream.readInt(): Int {
    val buffer = ByteArray(4)
    if (!readFully(buffer)) throw BackupException("The export file is damaged")
    return ((buffer[0].toInt() and 0xFF) shl 24) or
        ((buffer[1].toInt() and 0xFF) shl 16) or
        ((buffer[2].toInt() and 0xFF) shl 8) or
        (buffer[3].toInt() and 0xFF)
}

private fun InputStream.readLong(): Long {
    val buffer = ByteArray(8)
    if (!readFully(buffer)) throw BackupException("The export file is damaged")
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (buffer[i].toLong() and 0xFF)
    }
    return value
}

private fun InputStream.readByte(): Int {
    val value = read()
    if (value == -1) throw BackupException("The export file is damaged")
    return value
}

private fun InputStream.readSizedBlock(): ByteArray {
    val length = readInt()
    if (length < 0 || length > 32 * 1024 * 1024) {
        throw BackupException("The export file is damaged")
    }
    val buffer = ByteArray(length)
    if (!readFully(buffer)) throw BackupException("The export file is damaged")
    return buffer
}

/** Writes a big endian int. */
private fun OutputStream.writeInt(value: Int) {
    write(
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    )
}

/** Writes a big endian long. */
private fun OutputStream.writeLong(value: Long) {
    write(
        byteArrayOf(
            (value ushr 56).toByte(),
            (value ushr 48).toByte(),
            (value ushr 40).toByte(),
            (value ushr 32).toByte(),
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    )
}

/** Writes a single byte. */
private fun OutputStream.writeByte(value: Int) {
    write(value and 0xFF)
}
