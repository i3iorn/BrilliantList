package dev.schrammel.brilliantlist.core_data.media

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * Removes the EXIF metadata of an image while it is read.
 *
 * Photos taken with a phone carry GPS coordinates, the device model and a
 * timestamp. Those are part of the file forever - and the app stores the file
 * as it is, encrypted. Stripping the metadata on the way in keeps the
 * location of the person out of the journal.
 *
 * Only the container level metadata is removed, the pixels are untouched, so
 * there is no quality loss and no full decode of the bitmap.
 *
 * JPEG (APP1/Exif), PNG (eXIf chunk) and HEIC/HEIF (top-level Exif box) are
 * handled. HEIC/HEIF is the format iPhones use by default, so a photo taken
 * on an iPhone must not keep its location. The ISO Base Media File Format is
 * parsed at the top level only: the Exif box is dropped, everything else is
 * copied byte for byte. A 64-bit box size (size == 1) is not expected in a
 * photo and is passed through unchanged rather than risking corruption.
 *
 * The filter pulls: the bytes are copied straight from the source into the
 * buffer of the reader, nothing is buffered in between. A photo of any size
 * therefore costs no more memory than the buffer it is read into.
 */
object ExifStripper {

    /** A stream that yields the image without its EXIF metadata. */
    fun strip(source: InputStream): InputStream {
        val header = ByteArray(HEADER_SIZE)
        var read = 0
        while (read < HEADER_SIZE) {
            val r = source.read(header, read, HEADER_SIZE - read)
            if (r == -1) break
            read += r
        }
        if (read < HEADER_SIZE) {
            // Too short to be an image: pass it through unchanged.
            return ByteArrayInputStream(header.copyOf(read))
        }
        return when {
            // The JPEG has to be parsed from the first byte: the header can
            // already contain the start of the Exif segment.
            isJpeg(header) -> JpegExifFilter(ByteArray(0), SequenceInputStream(ByteArrayInputStream(header), source))
            isPng(header) -> PngExifFilter(header, source)
            else -> {
                // Check for HEIC/HEIF: the ftyp box is at the start and the
                // major brand follows the size and type.
                val ftypSize = readInt(header, 0)
                val type = String(header, 4, 4)
                if (type == "ftyp" && ftypSize >= HEIC_HEADER_SIZE) {
                    val brandBytes = ByteArray(4)
                    var brandRead = 0
                    while (brandRead < 4) {
                        val r = source.read(brandBytes, brandRead, 4 - brandRead)
                        if (r == -1) break
                        brandRead += r
                    }
                    if (brandRead == 4) {
                        val brand = String(brandBytes)
                        if (brand == "heic" || brand == "heix" || brand == "hevc" ||
                            brand == "hevx" || brand == "mif1" || brand == "msf1"
                        ) {
                            // The header is the 12 bytes we have read so far.
                            return HeicExifFilter(header + brandBytes, ftypSize, source)
                        }
                        // Not HEIC: the brand bytes are put back into the stream.
                        return SequenceInputStream(
                            ByteArrayInputStream(header + brandBytes),
                            source
                        )
                    }
                    // The brand could not be read: pass the bytes through.
                    return SequenceInputStream(
                        ByteArrayInputStream(header + brandBytes.copyOf(brandRead)),
                        source
                    )
                }
                // Not HEIC: pass it through unchanged.
                SequenceInputStream(ByteArrayInputStream(header), source)
            }
        }
    }

    private fun isJpeg(header: ByteArray): Boolean =
        header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()

    /** True for the 8-byte PNG signature. */
    private fun isPng(header: ByteArray): Boolean =
        header.size >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()

    /** Reads a 32-bit big-endian integer from [header] at [offset]. */
    private fun readInt(header: ByteArray, offset: Int): Long {
        return ((header[offset].toLong() and 0xFF) shl 24) or
            ((header[offset + 1].toLong() and 0xFF) shl 16) or
            ((header[offset + 2].toLong() and 0xFF) shl 8) or
            (header[offset + 3].toLong() and 0xFF)
    }

    /** The size of the JPEG/PNG header that is read first. */
    private const val HEADER_SIZE = 8

    /** The size of the HEIC/HEIF header: size, type and major brand. */
    private const val HEIC_HEADER_SIZE = 12
}

/**
 * The shared machinery of the filters.
 *
 * The bytes are pulled from the source and written straight into the buffer
 * of the reader. A segment that is dropped is read and thrown away, a
 * segment that is kept is copied over.
 */
private abstract class MetadataFilter(
    private val header: ByteArray,
    protected val source: InputStream
) : InputStream() {

    /** The bytes of the header that have not been copied out yet. */
    private var headerPosition = 0

    /** The bytes of the current segment that are still to be copied. */
    private var pending = ByteArray(32)
    private var pendingLength = 0
    private var pendingPosition = 0

    /** How many bytes of the current segment are still to be copied. */
    private var payloadRemaining = 0L

    private var finished = false

    /** True when the file was read completely. */
    private var atEnd = false

    override fun read(): Int {
        val single = ByteArray(1)
        val count = read(single, 0, 1)
        return if (count <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var written = 0
        while (written < len) {
            when {
                // The header comes first.
                headerPosition < header.size -> {
                    b[off + written] = header[headerPosition++]
                    written++
                }

                // The rest of the segment that is currently copied.
                pendingPosition < pendingLength -> {
                    val count = minOf(len - written, pendingLength - pendingPosition)
                    System.arraycopy(pending, pendingPosition, b, off + written, count)
                    pendingPosition += count
                    written += count
                }

                // The payload of the segment.
                payloadRemaining > 0 -> {
                    val count = source.read(
                        b,
                        off + written,
                        minOf((len - written).toLong(), payloadRemaining).toInt()
                    )
                    if (count <= 0) {
                        atEnd = true
                        break
                    }
                    payloadRemaining -= count
                    written += count
                }

                // The end of the file.
                atEnd -> break

                // The next segment has to be parsed first.
                else -> if (!parseNextSegment()) {
                    atEnd = true
                }
            }
        }
        return if (written == 0) -1 else written
    }

    /**
     * Parses the next segment.
     *
     * Returns false when the end of the file was reached.
     */
    protected abstract fun parseNextSegment(): Boolean

    /** Reads [count] bytes from the source, or null at the end of the file. */
    protected fun readSource(count: Int): ByteArray? {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val r = source.read(out, read, count - read)
            if (r == -1) return null
            read += r
        }
        return out
    }

    /** Reads a single byte from the source, or -1 at the end of the file. */
    protected fun readSourceByte(): Int = source.read()

    /** Throws the next [count] bytes away. */
    protected fun skipSource(count: Int): Boolean {
        val buffer = ByteArray(minOf(count, 8192))
        var remaining = count
        while (remaining > 0) {
            val read = source.read(buffer, 0, minOf(remaining, buffer.size))
            if (read <= 0) return false
            remaining -= read
        }
        return true
    }

    /** Puts the bytes of the segment header into the pending buffer. */
    protected fun setPending(bytes: ByteArray) {
        if (bytes.size > pending.size) {
            pending = bytes.copyOf()
        } else {
            bytes.copyInto(pending)
        }
        pendingLength = bytes.size
        pendingPosition = 0
    }

    /** Sets how many bytes of the payload are still to be copied. */
    protected fun setPayloadRemaining(count: Long) {
        payloadRemaining = count
    }
}

/**
 * Filters the APP1/Exif segment out of a JPEG.
 *
 * A JPEG is a sequence of segments, each with a length field, so a segment
 * can be removed without touching the rest of the file. The entropy coded
 * scan data at the end is copied verbatim.
 */
private class JpegExifFilter(
    header: ByteArray,
    source: InputStream
) : MetadataFilter(header, source) {

    /** True after the start of scan marker: the rest is entropy coded data. */
    private var inScanData = false

    override fun parseNextSegment(): Boolean {
        if (inScanData) return copyScanData()

        val marker = readSourceByte()
        if (marker == -1) return false
        val second = readSourceByte()
        if (second == -1) return false

        if (marker != 0xFF) {
            // Not a marker: copy the byte as it is.
            setPending(byteArrayOf(marker.toByte()))
            return true
        }
        if (second == 0xD9) {
            // End of image.
            setPending(byteArrayOf(0xFF.toByte(), second.toByte()))
            return false
        }
        if (second in 0xD0..0xD9 || second == 0x01) {
            // Standalone marker without a payload (the SOI is one of them).
            setPending(byteArrayOf(0xFF.toByte(), second.toByte()))
            return true
        }

        val lengthBytes = readSource(2) ?: return false
        val length = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)
        if (length < 2) {
            // Broken file: copy the rest unchanged.
            setPending(byteArrayOf(0xFF.toByte(), second.toByte()))
            return false
        }

        val payloadLength = length - 2
        if (second == 0xE1) {
            // The metadata is in an APP1 segment. Its first bytes say what it
            // is, so only the Exif segment is dropped.
            val peek = readSource(minOf(payloadLength, EXIF_HEADER.size))
            if (peek != null && peek.startsWithExif(EXIF_HEADER)) {
                // Drop the segment: the length field and the payload go.
                skipSource(payloadLength - peek.size)
                return true
            }
            // Not Exif: the segment is kept as it is.
            setPending(
                byteArrayOf(0xFF.toByte(), second.toByte()) + lengthBytes +
                    (peek ?: ByteArray(0))
            )
            setPayloadRemaining((payloadLength - (peek?.size ?: 0)).toLong())
            return true
        }

        setPending(byteArrayOf(0xFF.toByte(), second.toByte()) + lengthBytes)
        setPayloadRemaining(payloadLength.toLong())
        // After the start of scan the entropy coded data follows, it can
        // contain bytes that look like a marker.
        if (second == 0xDA) inScanData = true
        return true
    }

    /**
     * Copies the entropy coded data until the end of the image.
     *
     * The data can contain 0xFF bytes, so the end of the image marker is
     * looked for instead of every marker. The data is copied in blocks, so a
     * photo of any size is read without a lot of work.
     */
    private fun copyScanData(): Boolean {
        val block = ByteArray(SCAN_BLOCK_SIZE)
        val read = source.read(block)
        if (read <= 0) return false

        for (i in 0 until read - 1) {
            if (block[i] == 0xFF.toByte() && block[i + 1] == 0xD9.toByte()) {
                // The end of the image.
                setPending(block.copyOfRange(0, i + 2))
                return false
            }
        }
        if (block[read - 1] == 0xFF.toByte()) {
            // The marker can be split over two blocks.
            val next = readSourceByte()
            if (next == -1) {
                setPending(block.copyOf(read))
                return false
            }
            setPending(block.copyOf(read) + next.toByte())
            return next != 0xD9
        }
        setPending(block.copyOf(read))
        return true
    }

    private companion object {
        /** "Exif\0\0" - the start of the Exif payload in an APP1 segment. */
        val EXIF_HEADER = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00
        )

        /** How many bytes of the scan data are read at once. */
        const val SCAN_BLOCK_SIZE = 8192
    }
}

/**
 * Filters the eXIf chunk out of a PNG.
 *
 * Every chunk has its own CRC, so removing a chunk keeps the file valid.
 */
private class PngExifFilter(
    header: ByteArray,
    source: InputStream
) : MetadataFilter(header, source) {

    override fun parseNextSegment(): Boolean {
        val lengthBytes = readSource(4) ?: return false
        val length = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
            ((lengthBytes[1].toInt() and 0xFF) shl 16) or
            ((lengthBytes[2].toInt() and 0xFF) shl 8) or
            (lengthBytes[3].toInt() and 0xFF)
        val typeBytes = readSource(4) ?: return false
        val type = String(typeBytes, Charsets.US_ASCII)
        val data = readSource(length) ?: return false
        val crc = readSource(4) ?: return false
        if (type == "eXIf") {
            // Metadata chunk: drop it.
            return true
        }
        setPending(lengthBytes + typeBytes + data + crc)
        return true
    }
}

/**
 * Filters the Exif box out of a HEIC/HEIF file.
 *
 * A HEIC/HEIF file is a sequence of boxes. The Exif data is in a top-level
 * box named "Exif". The box is dropped; every other box is copied byte for
 * byte. The boxes are read with their 32-bit size, which is what cameras
 * write. A 64-bit size (size == 1) is passed through unchanged: the parser
 * does not expect it in a photo and must not corrupt the file.
 */
private class HeicExifFilter(
    header: ByteArray,
    private val ftypSize: Long,
    source: InputStream
) : MetadataFilter(header, source) {

    /** The bytes of the ftyp box that still have to be copied. */
    private var ftypRemaining = ftypSize - HEIC_HEADER_SIZE

    override fun parseNextSegment(): Boolean {
        // The rest of the ftyp box is copied as it is.
        if (ftypRemaining > 0) {
            setPayloadRemaining(ftypRemaining)
            ftypRemaining = 0
            return true
        }

        // The next box header: 4 bytes of size, 4 bytes of type.
        val boxHeader = readSource(8) ?: return false
        val size = readInt(boxHeader, 0)
        val type = String(boxHeader, 4, 4)

        if (size == 0L) {
            // The box extends to the end of the file.
            setPending(boxHeader)
            setPayloadRemaining(Long.MAX_VALUE)
            return true
        }

        if (size < 8) {
            // Invalid box size: copy the rest to avoid corruption.
            setPending(boxHeader)
            setPayloadRemaining(Long.MAX_VALUE)
            return true
        }

        if (type == "Exif") {
            // The metadata box: its payload is dropped.
            skipSource((size - 8).toInt())
            return true
        }

        // Everything else is copied as it is.
        setPending(boxHeader)
        setPayloadRemaining(size - 8)
        return true
    }
}

/** Reads a 32-bit big-endian integer from [bytes] at [offset]. */
private fun readInt(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}

/** The size of the HEIC/HEIF header: size, type and major brand. */
private const val HEIC_HEADER_SIZE = 12

/** True when this array starts with [prefix]. */
private fun ByteArray.startsWithExif(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}
