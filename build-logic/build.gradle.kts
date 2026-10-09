import org.gradle.api.artifacts.dsl.LockMode

plugins {
    `kotlin-dsl`
}

// wp:supply-chain
// The convention plugins compile against these jars on every build, so they are locked like the project's own
// configurations (build-logic/gradle.lockfile, written by `./gradlew resolveAndLockAll --write-locks`).
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
    implementation(libs.embedded.postgres)
    implementation(platform(libs.embedded.postgres.binaries.bom))
}
