// Native CLI gates. Each tool is pinned in gradle/native-tools.toml: Gradle downloads its release once into
// the Gradle user home, checks the SHA-256, and runs it, so no install is needed (a corporate laptop without
// admin rights, no Docker). Gates are UP-TO-DATE until their inputs change.
//   -Pslopproof.tools.mirror=<url>  fetch from an internal mirror instead of https://github.com/
//   -Pslopproof.tools.skip=true     skip every gate (offline laptop); the skip is logged loudly
//   -Pslopproof.tools.home=<dir>    cache directory (default: GRADLE_USER_HOME/slopproof-tools)

import org.gradle.api.tasks.TaskProvider
import slopproof.tools.GENERATED_REPO_DIRS
import slopproof.tools.GLOB_PREFIX
import slopproof.tools.NATIVE_TOOLS_MANIFEST
import slopproof.tools.NativeToolTask
import slopproof.tools.OpenApiBreakingTask
import slopproof.tools.assetOf
import slopproof.tools.parseToolManifest
import slopproof.tools.pinnedTool
import slopproof.tools.platformKey
import java.io.File

val toolManifest =
    providers
        .fileContents(layout.projectDirectory.file(NATIVE_TOOLS_MANIFEST))
        .asText
        .map { parseToolManifest(it, NATIVE_TOOLS_MANIFEST) }
val hostPlatform =
    providers.systemProperty("os.name").zip(providers.systemProperty("os.arch")) { os, arch ->
        platformKey(os, arch)
    }
val mirrorSetting = providers.gradleProperty("slopproof.tools.mirror")
val skipSetting = providers.gradleProperty("slopproof.tools.skip").map { it.toBoolean() }.orElse(false)
val toolRootSetting =
    providers
        .gradleProperty("slopproof.tools.home")
        .orElse(File(gradle.gradleUserHomeDir, "slopproof-tools").path)
val root = layout.projectDirectory
val repoSources = root.dir(".").asFileTree.matching { exclude(GENERATED_REPO_DIRS) }
val openApiSpec = "api/openapi.yaml"

// Registers one gate: the tool's pinned release and the shared wiring, then [setup] for its arguments and inputs.
fun <T : NativeToolTask> gate(
    name: String,
    type: Class<T>,
    tool: String,
    setup: T.() -> Unit,
): TaskProvider<T> =
    tasks.register(name, type) {
        group = "verification"
        description = "Runs $tool over the project (native CLI gate, pinned in $NATIVE_TOOLS_MANIFEST)."
        toolName.set(tool)
        toolVersion.set(toolManifest.map { pinnedTool(it, tool).version })
        val asset = toolManifest.zip(hostPlatform) { tools, platform -> assetOf(tools, tool, platform) }
        platform.set(hostPlatform)
        assetUrl.set(asset.map { it.url }.orElse(""))
        assetSha256.set(asset.map { it.sha256 }.orElse(""))
        assetEntry.set(asset.map { it.entry ?: "" }.orElse(""))
        projectDir.set(root)
        toolRoot.set(layout.dir(toolRootSetting.map { File(it) }))
        mirror.set(mirrorSetting)
        skipAll.set(skipSetting)
        marker.set(layout.buildDirectory.file("external-tools/$name.ok"))
        setup()
    }

val lintOpenApi =
    gate("lintOpenApi", NativeToolTask::class.java, "vacuum") {
        arguments.set(
            listOf(
                "lint",
                "--no-banner",
                "--no-update-check",
                "--ruleset",
                "config/vacuum/ruleset.yaml",
                "--details",
                "--fail-severity",
                "warn",
                openApiSpec,
            ),
        )
        scannedFiles.from(root.file(openApiSpec), root.dir("config/vacuum"))
    }

val checkApiBreaking =
    gate("checkApiBreaking", OpenApiBreakingTask::class.java, "oasdiff") {
        specPath.set(openApiSpec)
        scannedFiles.from(root.file(openApiSpec))
        // The base revision comes from git history, which is not a declared input: always run.
        outputs.upToDateWhen { false }
    }

val lintMigrations =
    gate("lintMigrations", NativeToolTask::class.java, "squawk") {
        arguments.set(listOf("--config", "config/squawk.toml", "${GLOB_PREFIX}src/main/resources/db/migration/*.sql"))
        scannedFiles.from(root.file("config/squawk.toml"), root.dir("src/main/resources/db/migration"))
    }

val astGrepScan =
    gate("astGrepScan", NativeToolTask::class.java, "ast-grep") {
        // --error makes every rule fail the gate, whatever severity its file declares.
        arguments.set(listOf("scan", "--config", "sgconfig.yml", "--error"))
        scannedFiles.from(repoSources)
    }

val astGrepTest =
    gate("astGrepTest", NativeToolTask::class.java, "ast-grep") {
        arguments.set(listOf("test", "--config", "sgconfig.yml"))
        scannedFiles.from(root.file("sgconfig.yml"), root.dir("config/ast-grep"))
    }

val opengrepScan =
    gate("opengrepScan", NativeToolTask::class.java, "opengrep") {
        arguments.set(listOf("scan", "--config", "config/opengrep", "--error", "--disable-version-check", "src/main/java"))
        scannedFiles.from(root.dir("config/opengrep"), root.dir("src/main/java"))
    }

val scanSecrets =
    gate("scanSecrets", NativeToolTask::class.java, "gitleaks") {
        arguments.set(listOf("dir", ".", "--config", "config/gitleaks.toml", "--redact", "--no-banner", "--no-color"))
        scannedFiles.from(repoSources)
    }

val scanVulnerabilities =
    gate("scanVulnerabilities", NativeToolTask::class.java, "osv-scanner") {
        arguments.set(listOf("scan", "source", "--lockfile", "gradle.lockfile"))
        requiredPath.set("gradle.lockfile")
        scannedFiles.from(root.file("gradle.lockfile"))
    }

if (skipSetting.get()) {
    logger.warn("slopproof: -Pslopproof.tools.skip=true, so the native CLI gates (externalChecks) will not run; the build is NOT verified")
}

val externalChecks =
    tasks.register("externalChecks") {
        group = "verification"
        description =
            "Runs every native CLI gate: OpenAPI lint and breaking changes, migration lint, ast-grep, opengrep, secrets, vulnerabilities."
        dependsOn(lintOpenApi, checkApiBreaking, lintMigrations, astGrepScan, astGrepTest, opengrepScan, scanSecrets, scanVulnerabilities)
    }

pluginManager.withPlugin("base") {
    tasks.named("check") { dependsOn(externalChecks) }
}
