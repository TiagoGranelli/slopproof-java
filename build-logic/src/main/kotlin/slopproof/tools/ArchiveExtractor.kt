package slopproof.tools

import org.gradle.api.GradleException
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

private const val TAR_BLOCK = 512

/**
 * Hands the body of one entry of a `.zip` or `.tar.gz` archive to [consume] and returns what it returns, or null
 * when the archive has no such entry. Only the named entry is read, so an archive cannot make the caller touch
 * any other data.
 */
internal fun <T> withEntry(
    archive: File,
    entry: String,
    consume: (InputStream) -> T,
): T? =
    when {
        archive.name.endsWith(".zip") -> withZipEntry(archive, entry, consume)
        archive.name.endsWith(".tar.gz") -> withTarEntry(archive, entry, consume)
        else -> throw GradleException("slopproof: ${archive.name} is neither a .zip nor a .tar.gz archive")
    }

/** Copies one entry to [target]. Returns false when the archive has no such entry. */
internal fun extractEntry(
    archive: File,
    entry: String,
    target: File,
): Boolean {
    target.parentFile.mkdirs()
    return withEntry(archive, entry) { body -> createNew(target).use { body.copyTo(it) } } != null
}

/**
 * The SHA-256 of one entry, computed while it streams past. Nothing is written to disk, so no path in the cache can
 * be raced or redirected between the check and the hash. Null when the archive has no such entry.
 */
internal fun entrySha256(
    archive: File,
    entry: String,
): String? =
    withEntry(archive, entry) { body ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(body, digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

/**
 * Opens [target] for writing only if nothing exists at that path. A planted link or an existing file is refused,
 * so an extraction can never write through a symbolic link into another location.
 */
private fun createNew(target: File): OutputStream =
    Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

private fun <T> withZipEntry(
    archive: File,
    entry: String,
    consume: (InputStream) -> T,
): T? =
    ZipInputStream(archive.inputStream().buffered()).use { zip ->
        val found = generateSequence { zip.nextEntry }.firstOrNull { it.name.removePrefix("./") == entry }
        if (found == null) null else consume(zip)
    }

/**
 * Reads ustar headers and skips everything but the named regular file. Pax and GNU long-name records are
 * skipped, so the entry name must fit the 100-byte ustar name field; the release archives pinned here do.
 */
private fun <T> withTarEntry(
    archive: File,
    entry: String,
    consume: (InputStream) -> T,
): T? =
    GZIPInputStream(archive.inputStream().buffered()).use { tar ->
        val header = ByteArray(TAR_BLOCK)
        while (readHeader(tar, header)) {
            val size = octal(header, 124, 12)
            check(size >= 0) { "slopproof: ${archive.name} has a negative entry size; the archive is malformed" }
            if (isRegularFile(header) && tarName(header) == entry) {
                return consume(boundedStream(tar, size))
            }
            tar.skipNBytes(paddedSize(size))
        }
        null
    }

/** The first [size] bytes of [tar]. A tar that ends inside the entry is an error, not a short entry. */
private fun boundedStream(
    tar: InputStream,
    size: Long,
): InputStream =
    object : InputStream() {
        private var remaining = size

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (remaining == 0L) return -1
            val read = tar.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("slopproof: tar archive ends inside an entry")
            remaining -= read
            return read
        }
    }

/** Reads one 512-byte header. False at end of file or at the zero block that ends a tar archive. */
private fun readHeader(
    tar: InputStream,
    header: ByteArray,
): Boolean {
    if (tar.readNBytes(header, 0, TAR_BLOCK) != TAR_BLOCK) return false
    return header.any { it != 0.toByte() }
}

private fun isRegularFile(header: ByteArray): Boolean = header[156] == '0'.code.toByte() || header[156] == 0.toByte()

/** The full path: the ustar prefix field (offset 345) joined to the name field, so `x/vacuum` never matches `vacuum`. */
private fun tarName(header: ByteArray): String {
    val name = String(header, 0, 100, StandardCharsets.UTF_8).substringBefore('\u0000')
    val prefix = String(header, 345, 155, StandardCharsets.UTF_8).substringBefore('\u0000')
    return (if (prefix.isEmpty()) name else "$prefix/$name").removePrefix("./")
}

private fun octal(
    header: ByteArray,
    offset: Int,
    length: Int,
): Long {
    val digits = String(header, offset, length, StandardCharsets.US_ASCII).trim { it <= ' ' }
    return if (digits.isEmpty()) 0L else digits.toLong(8)
}

private fun paddedSize(size: Long): Long = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK
