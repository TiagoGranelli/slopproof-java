pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Downloads the JDK 25 toolchain when none is installed. Behind a corporate proxy without access to
    // api.foojay.io, install JDK 25 and point org.gradle.java.installations.paths at it instead.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Repositories live here only, so a build script cannot add an unvetted one.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "slopproof-java"
