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
    // Security overrides of the Boot BOM; the catalog says why and when to remove them.
    implementation(platform(libs.jackson.bom))
    constraints {
        implementation(libs.tomcat.embed.core)
        implementation(libs.tomcat.embed.el)
        implementation(libs.tomcat.embed.websocket)
    }
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.jspecify)
    // Spring's own classes reference these annotations, and SpotBugs fails on classes it cannot resolve. Compile-only
    // puts them on its classpath without shipping them.
    compileOnly(libs.jetbrains.annotations)
    // The SuppressFBWarnings annotation for the constructor-injection suppressions in posts. Same version as the
    // SpotBugs tool; it is not shipped in the jar.
    compileOnly(libs.spotbugs.annotations)
    // Test code compiles against the main classes, whose annotations must resolve, or javac warns under -Werror.
    configurations.named("testCompileOnly") { extendsFrom(configurations.getByName("compileOnly")) }

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 moved MockMvc auto-configuration out of spring-boot-starter-test into this starter.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(platform(libs.jackson2.bom))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
