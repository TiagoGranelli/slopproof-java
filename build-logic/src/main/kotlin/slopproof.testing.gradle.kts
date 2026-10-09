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
    testImplementation(libs.findLibrary("swagger-parser").get())
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

// Where the API tests record the documented responses they reach. Each test JVM writes its own file, and the
// contractCoverage task reads them all, so the directory is shared by every fork of a run.
val contractHitsDirectory = layout.buildDirectory.dir("contract-hits")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.files(mockitoAgent).withPropertyName("mockitoAgent")
    // Local copies, so the lambda below captures the providers and not the script, which the configuration
    // cache cannot serialize. A Provider passed to systemProperty would reach the JVM as its toString.
    val agentArgument = mockitoAgentArgument
    val hitsDirectory = contractHitsDirectory
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                agentArgument.get(),
                "-Dslopproof.contractHits=${hitsDirectory.get().asFile.absolutePath}",
            )
        },
    )
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
    // Each run starts without hits, so a file from an earlier run cannot satisfy the contract gate. The local
    // copy keeps the lambda from capturing the script, as in the test defaults above.
    val hitsDirectory = contractHitsDirectory
    doFirst { hitsDirectory.get().asFile.deleteRecursively() }
    filter { excludeTestsMatching("*ContractCoverageTest") }
}

// Fails when an operation or a documented status of api/openapi.yaml is reached by no API test. It runs after
// every test so that all the hits are recorded.
// The source set is looked up here: inside the task's own configuration, `the<SourceSetContainer>` is not an
// extension of the Test task.
val testSourceSet = the<SourceSetContainer>().named("test")
val contractCoverage =
    tasks.register<Test>("contractCoverage") {
        group = "verification"
        description = "Fails when a documented operation or response status has no API test that reaches it."
        testClassesDirs = testSourceSet.get().output.classesDirs
        classpath = testSourceSet.get().runtimeClasspath
        filter { includeTestsMatching("*ContractCoverageTest") }
        dependsOn(tasks.test)
    }

// Gradle skips JaCoCo's verification when no test wrote execution data, and the contract gate treats an absent
// test tree as no tests to run. This task fails instead, so deleting the tests cannot pass `check`. It has no
// outputs, so it runs on every build.
val requireApiTests =
    tasks.register("requireApiTests") {
        group = "verification"
        description = "Fails when the test classes lack the contract coverage test, so that no gate passes empty."
        dependsOn(tasks.named("testClasses"))
        // A local copy, so the action below captures the file collection and not the script.
        val testClassesOutput = testSourceSet.get().output.classesDirs
        doLast {
            val contractTests = testClassesOutput.asFileTree.matching { include("**/ContractCoverageTest.class") }
            if (contractTests.isEmpty) {
                throw GradleException("no API tests were compiled: src/test must hold the contract coverage test")
            }
        }
    }

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, contractCoverage, requireApiTests)
}

pitest {
    pitestVersion = libs.findVersion("pitest").get().requiredVersion
    junit5PluginVersion = libs.findVersion("pitest-junit5").get().requiredVersion
    targetClasses = setOf("com.example.slopproof.*")
    targetTests = setOf("com.example.slopproof.*")
    // The contract gate reads the hits of the other tests, so PIT's own run has nothing to read.
    excludedTestClasses = setOf("com.example.slopproof.support.ContractCoverageTest")
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
