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
    implementation(libs.jspecify)

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
