// The Spring Boot plugin (executable jar, bootRun). Applied here because the root build resolves
// plugins only from build-logic, which pins their versions through the version catalog.
plugins {
    id("org.springframework.boot")
}
