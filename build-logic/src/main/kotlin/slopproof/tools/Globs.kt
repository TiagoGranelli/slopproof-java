package slopproof.tools

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files

/** Argument prefix for a pattern that the build expands itself: Windows has no shell to do it. */
const val GLOB_PREFIX = "glob:"

private const val WILDCARDS = "*?[{"

/**
 * The project-relative files matching [pattern], such as `db/migration` followed by `*.sql`; a double star
 * matches any depth. Sorted, with `/` separators.
 * The literal leading directories are the walk root, so only the wildcard part is matched.
 */
fun matchFiles(projectDir: File, pattern: String): List<String> {
    val segments = pattern.split('/')
    val literal = segments.takeWhile { segment -> WILDCARDS.none { it in segment } }
    require(literal.size < segments.size) { "glob \"$pattern\" has no wildcard; pass a plain path instead" }
    val base = projectDir.toPath().resolve(literal.joinToString("/"))
    if (!Files.isDirectory(base)) return emptyList()
    val matcher = FileSystems.getDefault().getPathMatcher("glob:" + segments.drop(literal.size).joinToString("/"))
    val projectRoot = projectDir.toPath()
    Files.walk(base).use { paths ->
        return paths
            .filter { Files.isRegularFile(it) && matcher.matches(base.relativize(it)) }
            .map { projectRoot.relativize(it).joinToString("/") }
            .sorted()
            .toList()
    }
}

/** Build output and caches, excluded from the repository inputs of the gates that scan the whole project. */
val GENERATED_REPO_DIRS = listOf("build/**", ".gradle/**", ".kotlin/**", ".git/**", "build-logic/build/**", "build-logic/.gradle/**")
