package slopproof.tools

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest

/** A pinned tool for one platform, as the build sees it. [entry] is null for a plain executable. */
data class ToolRelease(
    val name: String,
    val version: String,
    val platform: String,
    val url: String,
    val sha256: String,
    val entry: String?,
)

private const val COMPLETE_MARKER = ".complete"
private const val LOCK_FILE = ".lock"

/** Leading hex digits of the pin that name the install directory; a pin change gets a new directory. */
private const val PIN_DIRECTORY_LENGTH = 16

/** Serializes installs inside this JVM; the file lock below serializes them across builds. */
private val jvmInstallLock = Any()

/**
 * Installs pinned releases under [root] as `<tool>/<version>/<platform>/<pin>/bin/<executable>`. The archive is
 * kept beside `bin/`, so the executable can be derived from it again on every use. A release is downloaded once,
 * verified against its SHA-256, extracted into a staging directory, and only then moved into place with a
 * `.complete` marker, so an interrupted install is never reused.
 */
internal class ToolInstaller(
    private val root: File,
    private val mirror: String?,
    private val logger: Logger,
    private val downloader: ToolDownloader = ToolDownloader(),
) {
    /** Returns the installed executable, downloading it first when it is missing or no longer matches its pin. */
    fun install(release: ToolRelease): File {
        val pinned = release.sha256.take(PIN_DIRECTORY_LENGTH)
        val dir = root.resolve("${release.name}/${release.version}/${release.platform}/$pinned")
        val executable = dir.resolve("bin/${executableName(release.name, release.platform)}")
        // The check runs under the lock too, so no build reads a directory that another build is replacing.
        return underLock {
            if (!isVerified(release, dir, executable)) fetch(release, dir, executable)
            executable
        }
    }

    /**
     * Reuses an install only when its archive still hashes to the pin and the executable matches what that archive
     * yields. Checking the executable against the archive, not against a hash recorded at install time, is what
     * stops a swapped binary from passing, even one whose marker was rewritten to match.
     */
    private fun isVerified(release: ToolRelease, dir: File, executable: File): Boolean {
        val archive = dir.resolve(archiveName(release))
        if (!dir.resolve(COMPLETE_MARKER).isFile || !archive.isFile || !executable.isFile) return false
        if (sha256Of(archive) != release.sha256) return false
        return sha256Of(executable) == expectedExecutableSha(release, archive)
    }

    /** The hash the executable must have: the pin itself for a plain binary, else the hash of its entry in the archive. */
    private fun expectedExecutableSha(release: ToolRelease, archive: File): String? {
        val entry = release.entry ?: return release.sha256
        return entrySha256(archive, entry)
    }

    private fun fetch(release: ToolRelease, dir: File, executable: File) {
        dir.parentFile.mkdirs()
        val staging = File(dir.parentFile, "${dir.name}.partial")
        deleteTree(staging)
        staging.mkdirs()
        try {
            val archive = staging.resolve(archiveName(release))
            val url = mirrorUrl(release.url, mirror)
            // Errors name the public GitHub URL, never the mirror URL, which may carry a token.
            val source = if (mirror.isNullOrBlank()) release.url else "${release.url} (via -Pslopproof.tools.mirror)"
            logger.lifecycle("slopproof: fetching ${release.name} ${release.version} (${release.platform})")
            downloader.download(url, archive, "${release.name} ${release.version} (${release.platform}) from $source")
            verifyChecksum(release, archive)
            materialize(release, archive, staging.resolve("bin/${executableName(release.name, release.platform)}"))
            staging.resolve(COMPLETE_MARKER).writeText(release.sha256 + "\n")
            deleteTree(dir)
            Files.move(staging.toPath(), dir.toPath())
        } finally {
            deleteTree(staging)
        }
        check(executable.isFile) { "slopproof: ${release.name} ${release.version} installed without ${executable.name}" }
    }

    private fun materialize(release: ToolRelease, archive: File, target: File) {
        target.parentFile.mkdirs()
        val entry = release.entry
        if (entry == null) {
            // Files.copy refuses an existing target, so a link planted at the target path is never written through.
            Files.copy(archive.toPath(), target.toPath())
        } else if (!extractEntry(archive, entry, target)) {
            throw GradleException(
                "slopproof: ${release.name} ${release.version} (${release.platform}) archive ${archive.name} has no " +
                    "entry \"$entry\". Fix the entry in gradle/native-tools.toml to the file's path inside the archive.",
            )
        }
        target.setExecutable(true, false)
    }

    private fun verifyChecksum(release: ToolRelease, archive: File) {
        val actual = sha256Of(archive)
        if (actual == release.sha256) return
        throw GradleException(
            "slopproof: SHA-256 mismatch for ${release.name} ${release.version} (${release.platform}), asset ${archive.name}.\n" +
                "  expected ${release.sha256}\n" +
                "  actual   $actual\n" +
                "The download was corrupted or altered, or the pinned hash in gradle/native-tools.toml is wrong. " +
                "Nothing was installed. Retry; if the release really was re-published, check it and update the hash.",
        )
    }

    private fun <T> underLock(block: () -> T): T = synchronized(jvmInstallLock) {
        root.mkdirs()
        FileChannel.open(root.toPath().resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }
}

private fun archiveName(release: ToolRelease): String = release.url.substringAfterLast('/')

/**
 * Deletes [file] and everything under it without following symbolic links, so a link inside the cache can never
 * delete files outside it.
 */
private fun deleteTree(file: File) {
    val path = file.toPath()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
}

/** Rewrites a github.com release URL onto [mirror], e.g. an Artifactory remote for GitHub. */
internal fun mirrorUrl(url: String, mirror: String?): String {
    if (mirror.isNullOrBlank()) return url
    return url.replaceFirst("https://github.com/", mirror.trimEnd('/') + "/")
}

internal fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(file.inputStream().buffered(), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
