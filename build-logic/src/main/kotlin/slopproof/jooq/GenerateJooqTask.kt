package slopproof.jooq

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jooq.codegen.GenerationTool

/**
 * Generates the jOOQ classes for the schema the Flyway migrations produce, on a throwaway PostgreSQL.
 * The output depends only on the migrations and the generator, so the build cache can reuse it.
 */
@CacheableTask
abstract class GenerateJooqTask : DefaultTask() {

    /** The versioned SQL files, applied in order to the throwaway database jOOQ reads. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationsDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    // Gradle empties a non-incremental task's output directory before it runs, so a table dropped by a
    // migration cannot leave its old class behind.
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        // The generator, Flyway and the PostgreSQL driver all live on build-logic's classpath, so they
        // are looked up through this class's loader rather than the thread context loader.
        val classLoader = GenerateJooqTask::class.java.classLoader
        val configuration = jooqConfiguration(outputDirectory.get().asFile, packageName.get())
        withMigratedDatabase(migrationsDirectory.get().asFile, classLoader) { dataSource ->
            // The same DataSource Flyway migrated, so jOOQ reads exactly the schema that was built and
            // never opens a connection through DriverManager.
            val tool = GenerationTool()
            tool.setDataSource(dataSource)
            tool.setClassLoader(classLoader)
            tool.run(configuration)
        }
    }
}
