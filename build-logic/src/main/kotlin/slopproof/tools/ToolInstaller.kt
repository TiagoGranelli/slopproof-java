package slopproof.tools

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
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

/** Serializes installs inside this JVM; the file lock below serializes them across builds. */
private val jvmInstallLock = Any()

/**
 * Installs pinned releases under [root] as `<tool>/<version>/<platform>/bin/<executable>`. A release is
 * downloaded once, verified against its SHA-256, extracted into a staging directory, and only then moved
 * into place with a `.complete` marker, so an interrupted install is never reused.
 */
internal class ToolInstaller(
    private val root: File,
    private val mirror: String?,
    private val logger: Logger,
    private val downloader: ToolDownloader = ToolDownloader(),
) {
    /** Returns the installed executable, downloading it first when it is not in [root] yet. */
    fun install(release: ToolRelease): File {
        val dir = root.resolve("${release.name}/${release.version}/${release.platform}")
        val executable = dir.resolve("bin/${executableName(release.name, release.platform)}")
        if (dir.resolve(COMPLETE_MARKER).isFile) return executable
        return underLock {
            if (!dir.resolve(COMPLETE_MARKER).isFile) fetch(release, dir, executable)
            executable
        }
    }

    private fun fetch(release: ToolRelease, dir: File, executable: File) {
        dir.parentFile.mkdirs()
        val staging = File(dir.parentFile, "${dir.name}.partial")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val archive = staging.resolve(release.url.substringAfterLast('/'))
            val url = mirrorUrl(release.url, mirror)
            logger.lifecycle("slopproof: fetching ${release.name} ${release.version} (${release.platform})")
            downloader.download(url, archive, "${release.name} ${release.version} (${release.platform})")
            verifyChecksum(release, archive)
            materialize(release, archive, staging.resolve("bin/${executableName(release.name, release.platform)}"))
            archive.delete()
            staging.resolve(COMPLETE_MARKER).writeText(release.url + "\n")
            dir.deleteRecursively()
            Files.move(staging.toPath(), dir.toPath())
        } finally {
            staging.deleteRecursively()
        }
        check(executable.isFile) { "slopproof: ${release.name} ${release.version} installed without ${executable.name}" }
    }

    private fun materialize(release: ToolRelease, archive: File, target: File) {
        target.parentFile.mkdirs()
        val entry = release.entry
        if (entry == null) {
            archive.copyTo(target, overwrite = true)
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

