import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// Generates the Spring server interfaces and DTOs from api/openapi.yaml, so a controller cannot drift
// from the contract: it must implement every generated method, and its types are the contract's.
plugins {
    java
    id("org.openapi.generator")
}

val generate = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("spring")
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml"))
    // Under build/ so it is never committed; the stable path is what the static-analysis excludes match.
    outputDir.set(layout.buildDirectory.dir("generated/openapi"))
    apiPackage.set("com.example.slopproof.api")
    modelPackage.set("com.example.slopproof.api.model")
    invokerPackage.set("com.example.slopproof.api")
    // Schema Post becomes PostDto, so a generated DTO cannot be mistaken for a domain type.
    modelNameSuffix.set("Dto")
    // Regenerate from scratch so a model removed from the contract does not linger as a stale class.
    cleanupOutput.set(true)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            // Spring Boot 4 is Jakarta EE and its default JSON mapper is Jackson 3.
            "useSpringBoot4" to "true",
            "useJakartaEe" to "true",
            "useJackson3" to "true",
            // Controllers implement the interfaces and get no default 501 bodies, so a missing method
            // fails to compile instead of answering 501 at runtime.
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "performBeanValidation" to "false",
            // Nullable fields are plain nullable types; JsonNullable would leak into every DTO.
            "openApiNullable" to "false",
            // Spring 7 deprecates org.springframework.lang.Nullable, which the generator emits by default
            // and -Werror rejects; JSpecify is the annotation the project already uses.
            "useJspecify" to "true",
            // No Swagger/OpenAPI annotations on the generated code: the contract is the spec file itself.
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            // A timestamp in every file would make the output differ on each run and defeat the cache.
            "hideGenerationTimestamp" to "true",
            // The contract says nextCursor is absent on the last page, so null must be left out of the JSON.
            "generateJsonIncludeAnnotations" to "true",
            // Pins the generator's default; left unset it warns on every run.
            "generateJsonSetterNullsAnnotations" to "false",
        ),
    )
}

// The path is built from the layout, not from the task's output property: mapping that property
// into the source set makes Gradle resolve the task's outputs during configuration.
sourceSets.named("main") {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
}

tasks.named("compileJava") {
    dependsOn(generate)
}

// Declared here, not left to transitive starters, because the generated sources import these directly.
// Versions come from the Spring Boot BOM that the root build imports into implementation. JSpecify is
// not in that BOM and is declared by the root build (libs.jspecify): the `libs` accessor is not visible
// inside a convention plugin.
dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-validation")
    "implementation"("jakarta.annotation:jakarta.annotation-api")
    "implementation"("com.fasterxml.jackson.core:jackson-annotations")
}
