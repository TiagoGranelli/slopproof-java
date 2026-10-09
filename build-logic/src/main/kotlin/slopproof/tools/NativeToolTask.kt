package slopproof.tools

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/** What a gate does this run: execute the CLI with [arguments], or skip it and say [reason]. */
sealed interface GatePlan {
    data class Run(
        val arguments: List<String>,
    ) : GatePlan

    data class Skip(
        val reason: String,
    ) : GatePlan
}

/**
 * Runs one pinned native CLI over the project. The task is UP-TO-DATE while its inputs are unchanged, because
 * its marker output is written only after the CLI passes. A non-zero exit fails the build.
 */
abstract class NativeToolTask : DefaultTask() {
    @get:Input
    abstract val toolName: Property<String>

    @get:Input
    abstract val toolVersion: Property<String>

    @get:Input
    abstract val platform: Property<String>

    /** Empty when the manifest pins no asset for [platform]; the run then fails and names the platform. */
    @get:Input
    abstract val assetUrl: Property<String>

    @get:Input
    abstract val assetSha256: Property<String>

    /** Path of the executable inside the archive; empty when the asset is a plain executable. */
    @get:Input
    abstract val assetEntry: Property<String>

    /** CLI arguments. A `glob:<pattern>` argument expands to the matching project files when the gate runs. */
    @get:Input
    abstract val arguments: ListProperty<String>

    /** When set and missing from the project, the gate is skipped with a log line instead of failing. */
    @get:Input
    @get:Optional
    abstract val requiredPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannedFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Internal
    abstract val toolRoot: DirectoryProperty

    @get:Internal
    abstract val mirror: Property<String>

    /**
     * From -Pslopproof.tools.skip=true. An input, so that a skipped run and the next real run differ and Gradle
     * never records a skip as a pass.
     */
    @get:Input
    abstract val skipAll: Property<Boolean>

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        // A skip writes no marker, so Gradle would call the next run UP-TO-DATE on an empty output and hide the skip.
        outputs.upToDateWhen { !skipAll.get() && plan() is GatePlan.Run }
    }

    @TaskAction
    fun run() {
        // A marker left by an earlier pass must not outlive a run that skips or fails.
        marker.get().asFile.delete()
        if (skipAll.get()) {
            logger.warn("slopproof: ${toolName.get()} NOT RUN because -Pslopproof.tools.skip=true. Run it before you merge.")
            return
        }
        when (val gate = plan()) {
            // A warning, not a lifecycle line, and no marker: a gate that checked nothing must not look like a pass,
            // and the next run must not be UP-TO-DATE on it.
            is GatePlan.Skip -> {
                logger.warn("slopproof: ${toolName.get()} NOT RUN: ${gate.reason}")
            }

            is GatePlan.Run -> {
                execute(gate.arguments)
                writeMarker()
            }
        }
    }

    /** What to run. Subclasses override it when the arguments depend on git state rather than declared inputs. */
    protected open fun plan(): GatePlan {
        val required = requiredPath.orNull
        if (required != null &&
            !projectDir
                .file(required)
                .get()
                .asFile
                .exists()
        ) {
            return GatePlan.Skip("$required does not exist yet")
        }
        return expandGlobArguments(arguments.get(), projectDir.get().asFile)
    }

    /** Runs [cli] with the installed tool. Protected so a subclass can run its own git commands the same way. */
    protected fun execute(cli: List<String>) {
        val release = release()
        val binary = ToolInstaller(toolRoot.get().asFile, mirror.orNull, logger).install(release)
        val result =
            execOperations.exec {
                executable = binary.absolutePath
                args(cli)
                workingDir = projectDir.get().asFile
                isIgnoreExitValue = true
            }
        if (result.exitValue != 0) {
            throw GradleException(
                "slopproof: ${release.name} ${release.version} (${release.platform}) exited with ${result.exitValue}. " +
                    "Fix the findings above, or the gate's config, then run the task again.",
            )
        }
    }

    /** Captures the stdout of a git command run in the project, or null when it exits non-zero. */
    protected fun gitOutput(vararg gitArguments: String): ByteArray? {
        val output = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                commandLine("git", *gitArguments)
                standardOutput = output
                errorOutput = OutputStream.nullOutputStream()
                workingDir = projectDir.get().asFile
                isIgnoreExitValue = true
            }
        return if (result.exitValue == 0) output.toByteArray() else null
    }

    private fun release(): ToolRelease {
        val url = assetUrl.get()
        if (url.isEmpty()) {
            throw GradleException(
                "slopproof: ${toolName.get()} ${toolVersion.get()} has no pinned asset for ${platform.get()} in " +
                    "gradle/native-tools.toml. Pin one for this platform, or run the gate on a platform the manifest covers.",
            )
        }
        return ToolRelease(
            name = toolName.get(),
            version = toolVersion.get(),
            platform = platform.get(),
            url = url,
            sha256 = assetSha256.get(),
            entry = assetEntry.get().ifEmpty { null },
        )
    }

    private fun writeMarker() {
        val file = marker.get().asFile
        file.parentFile.mkdirs()
        file.writeText("${toolName.get()} ${toolVersion.get()} ${platform.get()} ${assetSha256.get()}\n")
    }
}

private fun expandGlobArguments(
    arguments: List<String>,
    projectDir: File,
): GatePlan {
    val expanded = mutableListOf<String>()
    for (argument in arguments) {
        if (!argument.startsWith(GLOB_PREFIX)) {
            expanded += argument
            continue
        }
        val pattern = argument.removePrefix(GLOB_PREFIX)
        val matches = matchFiles(projectDir, pattern)
        if (matches.isEmpty()) return GatePlan.Skip("no file matches $pattern")
        expanded += matches
    }
    return GatePlan.Run(expanded)
}
