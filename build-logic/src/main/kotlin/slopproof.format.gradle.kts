// Formatting gate. Spotless hangs spotlessCheck on `check`, so a file the formatter would change fails
// the build and the message names the fix (`spotlessApply`). Formatters run in-process from the
// Maven artifacts above, so nothing needs Docker, Node, Python or a shell on Windows.
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import slopproof.hooks.InstallGitHooksTask

plugins {
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val googleJavaFormatVersion = libs.findVersion("google-java-format").get().requiredVersion
val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

// Build output and the Gradle caches are generated. Each target is a file tree rooted outside them (or
// excluding them, which prunes the walk): a target that walks build/ races the tasks writing there and
// fails with "Could not read path" under --parallel.
val generatedPaths = listOf("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.git/**")

fun sources(
    root: String,
    vararg includes: String,
) = fileTree(root) {
    include(*includes)
    exclude(generatedPaths)
}

configure<SpotlessExtension> {
    // The repo is LF (.gitattributes). Without this, a Windows JVM writes CRLF and every file flips.
    lineEndings = LineEnding.UNIX

    java {
        target(sources("src", "**/*.java"))
        googleJavaFormat(googleJavaFormatVersion)
    }

    kotlin {
        target(sources("build-logic/src", "**/*.kt"))
        ktlint(ktlintVersion)
    }

    kotlinGradle {
        target(sources(".", "*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts"))
        ktlint(ktlintVersion)
    }

    // Plain text formats: no formatter, only the whitespace rules every editor shares with .editorconfig.
    format("text") {
        target(sources(".", "*.md", "**/*.yaml", "**/*.yml", "**/*.sql", "**/*.toml", "**/*.xml"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// One command on any OS to turn the committed pre-commit hook on (see config/git-hooks/pre-commit).
// Not run by the build itself: a build must not rewrite a developer's git config.
tasks.register<InstallGitHooksTask>("installGitHooks") {
    group = "build setup"
    description = "Points git at config/git-hooks so the pre-commit hook runs spotlessCheck."
    hooksPath.set("config/git-hooks")
    repoDirectory.set(layout.projectDirectory)
}
