package slopproof.tools

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * checkApiBreaking: diffs the OpenAPI spec against its merge-base with main, so a breaking change fails the build
 * before the branch is merged. The base comes from git, so it is not a declared input; the gate always runs.
 */
abstract class OpenApiBreakingTask : NativeToolTask() {
    /** Project-relative path of the spec, e.g. `api/openapi.yaml`. */
    @get:Input
    abstract val specPath: Property<String>

    override fun plan(): GatePlan {
        val base =
            mergeBase()
                ?: return GatePlan.Skip("neither origin/main nor main exists to compare with; fetch the default branch")
        val spec = specPath.get()
        val baseSpec =
            gitOutput("show", "$base:$spec")
                ?: return GatePlan.Skip("$spec does not exist at merge-base $base, so there is no base to compare")
        val baseFile = projectDir.file("build/external-tools/checkApiBreaking/base-openapi.yaml").get().asFile
        baseFile.parentFile.mkdirs()
        baseFile.writeBytes(baseSpec)
        return GatePlan.Run(listOf("breaking", baseFile.absolutePath, spec, "--fail-on", "ERR"))
    }

    /** The merge-base of HEAD with origin/main, falling back to a local main; null when neither exists. */
    private fun mergeBase(): String? =
        listOf("origin/main", "main").firstNotNullOfOrNull { reference ->
            gitOutput("merge-base", "HEAD", reference)?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() }
        }
}
