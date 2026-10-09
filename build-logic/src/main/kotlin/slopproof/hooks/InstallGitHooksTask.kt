package slopproof.hooks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Points git at the hooks committed in `config/git-hooks`. It only writes `core.hooksPath`, so it is
 * safe to run again. It is a task, not a build step, so no build quietly changes git config.
 */
abstract class InstallGitHooksTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:Input
        abstract val hooksPath: Property<String>

        @get:Internal
        abstract val repoDirectory: DirectoryProperty

        @TaskAction
        fun install() {
            val repo = repoDirectory.get().asFile
            // A worktree's `.git` is a file, not a directory, so `exists()` covers both layouts.
            if (!repo.resolve(".git").exists()) {
                throw GradleException("installGitHooks needs a git checkout (no .git in ${repo.path})")
            }
            execOperations.exec {
                workingDir = repo
                commandLine("git", "config", "core.hooksPath", hooksPath.get())
            }
            logger.lifecycle("core.hooksPath set to ${hooksPath.get()}; the pre-commit hook now runs spotlessCheck")
        }
    }
