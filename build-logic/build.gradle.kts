plugins {
    `kotlin-dsl`
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
    // wp:jooq: embedded-postgres depends on the binaries of every platform (about 130 MB). Only the host's
    // are needed, and zonky finds them on the classpath by file name.
    implementation(libs.embedded.postgres) {
        exclude(group = "io.zonky.test.postgres")
    }
    implementation(platform(libs.embedded.postgres.binaries.bom))
    // wp:jooq: the BOM pins the version, so the artifact name alone selects the host's PostgreSQL 18.6.0.
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-${hostPostgresBinaries()}")
}

// wp:jooq: zonky ships one artifact per OS and CPU. Gradle runs on the machine the build runs on, so the
// JVM's own properties name the artifact this build needs.
fun hostPostgresBinaries(): String {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    return when {
        os.contains("win") -> "windows-amd64"
        os.contains("mac") -> if (arm) "darwin-arm64v8" else "darwin-amd64"
        arm -> "linux-arm64v8"
        else -> "linux-amd64"
    }
}
