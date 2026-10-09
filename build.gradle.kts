import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("slopproof.java-conventions")
    id("slopproof.openapi")
    id("slopproof.jooq")
    id("slopproof.static-analysis")
    id("slopproof.format")
    id("slopproof.testing")
    id("slopproof.supply-chain")
    id("slopproof.external-tools")
    id("slopproof.spring-boot")
}

group = "com.example"
version = "0.1.0"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.spring.modulith.bom))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation(libs.jspecify)
    // Spring's own classes reference these annotations, and SpotBugs fails on classes it cannot resolve. Compile-only
    // puts them on its classpath without shipping them. Not in the Spring Boot BOM, so the version is literal.
    compileOnly("org.jetbrains:annotations:26.0.2")
    // The SuppressFBWarnings annotation for the constructor-injection suppressions in posts. Same version as the
    // SpotBugs tool in the catalog; it is not shipped in the jar.
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 moved MockMvc auto-configuration out of spring-boot-starter-test into this starter.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
