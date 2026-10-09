// Test conventions: the JUnit Platform (JUnit Jupiter plus the jqwik property engine), JaCoCo coverage gates
// on `check`, and PIT mutation testing behind its own `mutationTest` task, which is too slow for every commit.
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.CommandLineArgumentProvider
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("jqwik").get())
    testImplementation(libs.findLibrary("archunit-junit5").get())
    testImplementation(libs.findLibrary("swagger-request-validator-mockmvc").get())
    testImplementation(libs.findLibrary("embedded-postgres").get())
    // embedded-postgres brings one binaries artifact per platform and picks the host's at runtime; the BOM
    // pins them all to PostgreSQL 18. Apple Silicon is not among its defaults.
    testImplementation(platform(libs.findLibrary("embedded-postgres-binaries-bom").get()))
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
}

// Mockito 5 attaches to the JVM at run time, and JDK 21+ warns about that. A future JDK will refuse it by
// default (JEP 451). Loading it as a javaagent avoids both. The version comes from the Spring Boot BOM, so it
// matches the mockito-core on the test classpath.
val mockitoAgent: Configuration =
    configurations.create("mockitoAgent") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
dependencies {
    mockitoAgent(platform(SpringBootPlugin.BOM_COORDINATES))
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}
val mockitoAgentArgument: Provider<String> =
    mockitoAgent.elements.map { agentFiles ->
        "-javaagent:${agentFiles.single().asFile.absolutePath}"
    }

// Generated code (OpenAPI stubs, jOOQ tables) and the entry point are not ours to cover or mutate.
val generatedAndEntryPoint =
    listOf(
        "com/example/slopproof/api/**",
        "com/example/slopproof/db/**",
        "com/example/slopproof/SlopproofApplication.class",
    )

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.files(mockitoAgent).withPropertyName("mockitoAgent")
    // A local copy, so the lambda below captures the provider and not the script, which the configuration
    // cache cannot serialize.
    val agentArgument = mockitoAgentArgument
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf(agentArgument.get()) })
    // Half the cores: each fork starts its own embedded Postgres, which is heavy on a laptop.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // Fails when test classes exist but JUnit finds none, e.g. a class that uses JUnit 4 annotations. With no
    // test classes at all the task is a no-op, so an empty skeleton still passes.
    failOnNoDiscoveredTests = true
    testLogging {
        // Passing tests stay silent; a failure prints its name and the full exception with causes.
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

val coveredClasses =
    the<SourceSetContainer>().named("main").map {
        it.output.asFileTree.matching { exclude(generatedAndEntryPoint) }
    }

tasks.jacocoTestReport {
    classDirectories.setFrom(coveredClasses)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(coveredClasses)
    // No test run leaves no execution data. Skip then, rather than fail an empty skeleton: there is nothing
    // to measure, and a run that did execute tests still has its data here and is checked.
    onlyIf("no JaCoCo execution data, so no tests ran") { executionData.files.any { it.exists() } }
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

pitest {
    pitestVersion = libs.findVersion("pitest").get().requiredVersion
    junit5PluginVersion = libs.findVersion("pitest-junit5").get().requiredVersion
    targetClasses = setOf("com.example.slopproof.*")
    targetTests = setOf("com.example.slopproof.*")
    excludedClasses =
        setOf(
            "com.example.slopproof.api.*",
            "com.example.slopproof.db.*",
            "com.example.slopproof.SlopproofApplication",
        )
    // Gates the mutation score and the line coverage PIT measures on its own run, not JaCoCo's.
    mutationThreshold = 70
    coverageThreshold = 80
    threads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    outputFormats = setOf("XML", "HTML")
    // One report directory per run, not a timestamped copy, so CI can publish a fixed path.
    timestampedReports = false
    // No incremental analysis: PIT 1.30 ships the history store only in the commercial Arcmutate plugin, and
    // with withHistory on, the open-source core fails with "no history plugin has been installed".
    jvmArgs.add(mockitoAgentArgument)
}

tasks.register("mutationTest") {
    group = "verification"
    description = "Runs PIT mutation testing; fails below the mutation and coverage thresholds. For CI or nightly."
    dependsOn(tasks.named("pitest"))
}
