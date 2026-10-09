// Static analysis that fails the build: Error Prone and NullAway at compile time, Checkstyle and PMD for
// readability limits and slop smells, a PMD copy-paste check, and SpotBugs with FindSecBugs. Every tool
// runs under `check`. Generated code under build/generated is skipped: the generators own its shape.
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
    id("net.ltgt.errorprone")
    checkstyle
    pmd
    id("com.github.spotbugs")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Error Prone and NullAway run inside javac, so they only see what the compiler sees.
dependencies {
    add("errorprone", libs.findLibrary("errorprone-core").get())
    add("errorprone", libs.findLibrary("nullaway").get())
    add("spotbugsPlugins", libs.findLibrary("findsecbugs").get())
}

// Files the generators write, and the regex form Error Prone takes (both separators, so Windows works).
val generatedDir =
    layout.buildDirectory
        .dir("generated")
        .get()
        .asFile
val generatedPathRegex = ".*[/\\\\]build[/\\\\]generated[/\\\\].*"

tasks.withType<JavaCompile>().configureEach {
    // The plugin's options are a Property-based object; name it so the calls bind to its members.
    val errorProne: ErrorProneOptions = options.errorprone
    errorProne.disableWarningsInGeneratedCode.set(true)
    errorProne.excludedPaths.set(generatedPathRegex)
    // NullAway only checks @NullMarked code; package-info.java marks each package. JSpecify mode reads
    // the type-use annotations that the marked packages use.
    errorProne.check("NullAway", CheckSeverity.ERROR)
    errorProne.option("NullAway:OnlyNullMarked", true)
    errorProne.option("NullAway:JSpecifyMode", true)
    // Checks that catch what agents leave behind: dead code, swallowed errors and stack traces.
    errorProne.check("UnusedVariable", CheckSeverity.ERROR)
    errorProne.check("CatchAndPrintStackTrace", CheckSeverity.ERROR)
    errorProne.check("EmptyCatch", CheckSeverity.ERROR)
    errorProne.check("SystemOut", CheckSeverity.ERROR)
    errorProne.check("ReturnValueIgnored", CheckSeverity.ERROR)
    errorProne.check("MissingOverride", CheckSeverity.ERROR)
    errorProne.check("FieldCanBeFinal", CheckSeverity.ERROR)
    errorProne.check("UnnecessaryLambda", CheckSeverity.ERROR)
}

checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configDirectory = layout.projectDirectory.dir("config/checkstyle")
    configProperties = mapOf("javadocScope" to "public")
    maxWarnings = 0
    isIgnoreFailures = false
}

// Test classes are not an API, so they need no Javadoc. Every other Checkstyle rule still applies.
tasks.named<Checkstyle>("checkstyleTest") {
    configProperties = mapOf("javadocScope" to "nothing")
}

pmd {
    toolVersion = libs.findVersion("pmd").get().requiredVersion
    ruleSetConfig = resources.text.fromFile("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    isConsoleOutput = true
    isIgnoreFailures = false
}

// Checkstyle and PMD read the sources, so they skip generated files by path. The exclude specs are stored
// with the task, so each captures a local File, not this script (which the configuration cache cannot store).
tasks.withType<Checkstyle>().configureEach {
    val generated = generatedDir
    exclude { it.file.startsWith(generated) }
}

tasks.withType<Pmd>().configureEach {
    val generated = generatedDir
    exclude { it.file.startsWith(generated) }
}

spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    effort = Effort.MAX
    // Low-confidence findings are mostly noise for a template; medium and up fail the build.
    reportLevel = Confidence.MEDIUM
    excludeFilter = layout.projectDirectory.file("config/spotbugs/exclude.xml")
    ignoreFailures = false
}

// The plugin writes no HTML report unless one is created, and the HTML is the one that names the findings.
tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
}

// PMD ships no copy-paste task in Gradle, so run its CPD command line tool directly. It runs in its own
// JVM from a detached configuration, so the PMD rule set does not leak into the build's classpath.
val pmdCli =
    configurations.create("pmdCli") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
dependencies {
    add("pmdCli", libs.findLibrary("pmd-cli").get())
    add("pmdCli", libs.findLibrary("pmd-java").get())
}

val cpd =
    tasks.register<JavaExec>("cpd") {
        group = "verification"
        description = "Fails when a block of at least 100 tokens is copy-pasted (PMD CPD)."
        // A local copy: the doLast below must not capture this script, which the configuration cache cannot store.
        val report =
            layout.buildDirectory
                .file("reports/cpd/cpd.txt")
                .get()
                .asFile
        classpath(pmdCli)
        mainClass = "net.sourceforge.pmd.cli.PmdCli"
        args(
            "cpd",
            "--language",
            "java",
            "--minimum-tokens",
            "100",
            "--format",
            "text",
            "--report-file",
            report.path,
            "--dir",
            "src",
        )
        inputs.files(fileTree("src") { include("**/*.java") })
        outputs.file(report)
        outputs.cacheIf { true }
        // CPD exits 4 when it finds duplicates. Gradle's own message for that names only the exit code, so this
        // one names the file that lists the blocks. The task reads the exit code itself, so it fails here.
        isIgnoreExitValue = true
        doLast {
            val exitValue = executionResult.get().exitValue
            if (exitValue != 0) {
                throw GradleException(
                    "PMD CPD exited with $exitValue (4 means duplicated code). " +
                        "See the duplicated blocks in $report",
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(cpd)
}
