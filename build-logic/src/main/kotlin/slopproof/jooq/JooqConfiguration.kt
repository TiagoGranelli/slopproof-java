package slopproof.jooq

import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Target
import java.io.File

/**
 * The codegen options for the migrated schema. Every option that changes the output is set here, so a
 * jOOQ upgrade that changes a default cannot change the generated code unnoticed.
 */
internal fun jooqConfiguration(
    outputDirectory: File,
    packageName: String,
): Configuration =
    Configuration().withGenerator(
        Generator()
            .withName("org.jooq.codegen.JavaGenerator")
            .withDatabase(
                Database()
                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                    .withInputSchema("public")
                    // Flyway's bookkeeping table is not part of the application schema.
                    .withExcludes("flyway_schema_history"),
            ).withGenerate(
                Generate()
                    .withRecords(true)
                    .withPojos(false)
                    .withDaos(false)
                    .withFluentSetters(false)
                    .withJavaTimeTypes(true)
                    // A timestamp in the annotation would change the output on every run and defeat the cache.
                    .withGeneratedAnnotation(true)
                    .withGeneratedAnnotationDate(false),
            ).withTarget(
                Target()
                    .withPackageName(packageName)
                    .withDirectory(outputDirectory.absolutePath)
                    .withClean(true),
            ),
    )
