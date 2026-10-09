// Java toolchain and compiler settings shared by every module.
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters keeps parameter names for Spring; -Werror makes every javac warning a failure.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Werror"))
}
