package slopproof.tools

import org.gradle.api.GradleException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

private const val TAR_BLOCK = 512

/**
 * Copies one entry out of a `.zip` or `.tar.gz` archive to [target]. Returns false when the archive has no
 * such entry. Only the named entry is written, so an archive cannot place files outside [target].
 */
internal fun extractEntry(archive: File, entry: String, target: File): Boolean {
    target.parentFile.mkdirs()
    return when {
        archive.name.endsWith(".zip") -> extractZip(archive, entry, target)
        archive.name.endsWith(".tar.gz") -> extractTarGz(archive, entry, target)
        else -> throw GradleException("slopproof: ${archive.name} is neither a .zip nor a .tar.gz archive")
    }
}

private fun extractZip(archive: File, entry: String, target: File): Boolean =
    ZipInputStream(archive.inputStream().buffered()).use { zip ->
        val found = generateSequence { zip.nextEntry }.firstOrNull { it.name.removePrefix("./") == entry }
        if (found == null) return false
        createNew(target).use { zip.copyTo(it) }
        true
    }

/**
 * Reads ustar headers and skips everything but the named regular file. Pax and GNU long-name records are
 * skipped, so the entry name must fit the 100-byte ustar name field; the release archives pinned here do.
 */
private fun extractTarGz(archive: File, entry: String, target: File): Boolean =
    GZIPInputStream(archive.inputStream().buffered()).use { tar ->
        val header = ByteArray(TAR_BLOCK)
        while (readHeader(tar, header)) {
            val size = octal(header, 124, 12)
            check(size >= 0) { "slopproof: ${archive.name} has a negative entry size; the archive is malformed" }
            if (isRegularFile(header) && tarName(header) == entry) {
                copyExactly(tar, target, size)
                return true
            }
            tar.skipNBytes(paddedSize(size))
        }
        false
    }

/** Reads one 512-byte header. False at end of file or at the zero block that ends a tar archive. */
private fun readHeader(tar: InputStream, header: ByteArray): Boolean {
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

private fun octal(header: ByteArray, offset: Int, length: Int): Long {
    val digits = String(header, offset, length, StandardCharsets.US_ASCII).trim { it <= ' ' }
    return if (digits.isEmpty()) 0L else digits.toLong(8)
}

private fun paddedSize(size: Long): Long = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK

/**
 * Opens [target] for writing only if nothing exists at that path. A planted link or an existing file is refused,
 * so an extraction can never write through a symbolic link into another location.
 */
private fun createNew(target: File): OutputStream =
    Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

private fun copyExactly(tar: InputStream, target: File, size: Long) {
    createNew(target).use { out ->
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(read >= 0) { "tar archive ends inside ${target.name}" }
            out.write(buffer, 0, read)
            remaining -= read
        }
    }
}
