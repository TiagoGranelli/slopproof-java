// Supply chain of the dependencies: what gets resolved (locks), what it contains (SBOM) and whether it may
// ship (licenses). Renovate (renovate.json) and CI (.github/workflows/ci.yml) sit around it.
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.file.RegularFile

plugins {
    id("org.cyclonedx.bom")
    id("com.github.jk1.dependency-license-report")
}

// STRICT: a resolution that differs from gradle.lockfile fails the build instead of moving a version silently.
// Every configuration is locked, including the test and tool classpaths, so `check` cannot pull in an unlocked
// jar. The lockfiles and gradle/verification-metadata.xml are written together by one command (AGENTS.md):
//   ./gradlew spotlessApply && ./gradlew --write-locks --write-verification-metadata sha256 check -x scanVulnerabilities
// It runs `check` rather than a task that resolves every configuration, because some configurations (PMD's
// auxclasspath, Spotless's formatters) exist or resolve only when their task is stored in the configuration
// cache. Gradle writes the locks only when the build succeeds, and osv-scanner reads the old lockfile, so it is
// left out of that run; the plain `check` after it runs it on the new locks. The metadata run needs an empty
// GRADLE_USER_HOME: from a warm cache Gradle skips the parent POMs, which a fresh clone then fails to verify.
// sha256 pins each artifact's checksum. `<verify-signatures>` stays false: PGP verification needs the signing
// keys from a key server, which corporate networks often block, and those keys would still be trusted by the
// repo rather than by a reviewer. The checksums already catch a changed artifact at the same coordinates.
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// The SBOM lists the runtime classpath: what the artifact actually ships. Build-time tools (Error Prone, the
// code generators) do not ship, so they stay out of it.
tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs.set(listOf("runtimeClasspath"))
    projectType.set(Component.Type.APPLICATION)
    jsonOutput.set(layout.buildDirectory.file("reports/sbom/bom.json"))
    // One format is enough: the XML copy would be a second, differently serialized SBOM to keep in sync.
    xmlOutput.convention(null as RegularFile?)
}

// The license check covers the runtime and test classpaths. Test-only libraries are not shipped, but a GPL test
// dependency is still a licensing problem for the repo, so they are not exempt.
licenseReport {
    configurations = arrayOf("runtimeClasspath", "testRuntimeClasspath")
    renderers = arrayOf(JsonReportRenderer("licenses.json"))
    // The bundle maps the names and URLs that POMs use ("The Apache License, Version 2.0") to SPDX ids, so the
    // allowlist holds one name per license. `false` drops the plugin's own rules: its defaults print display names
    // ("Apache License, Version 2.0"), not SPDX ids. A license with no rule keeps its raw name and fails the check.
    filters =
        arrayOf(
            LicenseBundleNormalizer(file("config/licenses/license-normalizer-bundle.json").path, false),
        )
    // A dependency passes when at least one of its licenses is listed. Weak copyleft is allowed because these
    // jars are consumed unmodified and linked at run time: LGPL, MPL, EPL-1.0 and CDDL. Strong copyleft (GPL,
    // AGPL) stays out, except GPL with the Classpath Exception, which the Jakarta APIs use for linking.
    // Go's license and EDL 1.0 are BSD-3-Clause text, so they map to that id rather than to a list entry.
    allowedLicensesFile = file("config/licenses/allowed-licenses.json")
}

// The plugin's tasks read the Project at execution time, which the configuration cache cannot store. Marking them
// lets Gradle skip the cache only for runs that include them, instead of failing on the plugin's own warning.
tasks.withType<com.github.jk1.license.task.ReportTask>().configureEach {
    notCompatibleWithConfigurationCache("com.github.jk1:gradle-license-report reads the Project at execution time")
}
tasks.withType<com.github.jk1.license.task.CheckLicenseTask>().configureEach {
    notCompatibleWithConfigurationCache("com.github.jk1:gradle-license-report reads the Project at execution time")
}
tasks.withType<com.github.jk1.license.task.CheckLicensePreparationTask>().configureEach {
    notCompatibleWithConfigurationCache("com.github.jk1:gradle-license-report reads the Project at execution time")
}

// The plugin does not declare its config files as inputs, so an edited allowlist or normalizer bundle would
// leave the previous verdict up to date.
tasks.named("generateLicenseReport") {
    inputs.file("config/licenses/license-normalizer-bundle.json")
}
tasks.named("checkLicense") {
    inputs.file("config/licenses/allowed-licenses.json")
}

tasks.named("check") {
    dependsOn("checkLicense")
}
