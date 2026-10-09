// Type-safe SQL for the posts table: jOOQ classes generated from the Flyway migrations on an embedded
// PostgreSQL, so the build needs no Docker and a query that names a missing column fails to compile.
import slopproof.jooq.GenerateJooqTask

plugins {
    java
}

val generateJooq =
    tasks.register<GenerateJooqTask>("generateJooq") {
        migrationsDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration"))
        outputDirectory.set(layout.buildDirectory.dir("generated/jooq"))
        packageName.set("com.example.slopproof.db")
    }

// The generated sources compile with the hand-written ones, under the same -Werror rules.
the<JavaPluginExtension>().sourceSets.named("main") {
    java.srcDir(generateJooq.map { it.outputDirectory })
}

tasks.named("compileJava") {
    dependsOn(generateJooq)
}

dependencies {
    // Versions come from the Spring Boot BOM that the root build imports as a platform.
    add("implementation", "org.springframework.boot:spring-boot-starter-jooq")
    add("implementation", "org.springframework.boot:spring-boot-starter-flyway")
    add("implementation", "org.flywaydb:flyway-database-postgresql")
    add("runtimeOnly", "org.postgresql:postgresql")
}
