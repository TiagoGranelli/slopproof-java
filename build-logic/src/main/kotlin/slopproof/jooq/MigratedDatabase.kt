package slopproof.jooq

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.time.Duration
import javax.sql.DataSource
import org.flywaydb.core.Flyway

/**
 * Starts a throwaway PostgreSQL 18, applies [migrations] to it and runs [block] on its DataSource.
 * The server stops when [block] returns or throws, so a failed migration leaves no process behind.
 */
internal fun <T> withMigratedDatabase(
    migrations: File,
    classLoader: ClassLoader,
    block: (DataSource) -> T,
): T =
    EmbeddedPostgres.builder()
        .setCleanDataDirectory(true)
        // The database is discarded after this run, so durability is not worth the fsync cost.
        .setServerConfig("fsync", "off")
        // First start extracts the binaries; a slow disk or an antivirus scan of them can exceed the default.
        .setPGStartupWait(Duration.ofMinutes(1))
        .start()
        .use { postgres ->
            val dataSource = postgres.postgresDatabase
            // Flyway finds its PostgreSQL support through ServiceLoader, so name the loader that holds
            // flyway-database-postgresql rather than relying on the thread context loader.
            Flyway.configure(classLoader)
                .dataSource(dataSource)
                .locations("filesystem:${migrations.absolutePath}")
                .load()
                .migrate()
            block(dataSource)
        }
