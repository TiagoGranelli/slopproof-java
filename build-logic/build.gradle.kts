import org.gradle.api.artifacts.dsl.LockMode

plugins {
    `kotlin-dsl`
}

// The convention plugins compile against these jars on every build, so they are locked like the project's own
// configurations (build-logic/gradle.lockfile, written by the command in AGENTS.md).
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// A plugin from the version catalog, as the coordinates of its marker artifact, so the convention
// plugins in src/main/kotlin can apply it by id without a version.
fun plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(plugin(libs.plugins.spring.boot))
    implementation(plugin(libs.plugins.openapi.generator))
    implementation(plugin(libs.plugins.errorprone))
    implementation(plugin(libs.plugins.spotbugs))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.pitest))
    implementation(plugin(libs.plugins.cyclonedx))
    implementation(plugin(libs.plugins.license.report))
    // jOOQ code generation runs Flyway against an embedded PostgreSQL inside the build (no Docker).
    implementation(libs.jooq.codegen)
    implementation(libs.jooq.meta)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    // embedded-postgres depends on the binaries of Windows, Linux (glibc and Alpine) and Intel macOS, and
    // finds the host's at runtime. All of them stay on the classpath on purpose: the dependency lockfile and
    // verification metadata are shared by Windows laptops and Linux CI, so they must list every platform.
    implementation(libs.embedded.postgres)
    implementation(platform(libs.embedded.postgres.binaries.bom))
    // Apple Silicon is not among the defaults.
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
}
