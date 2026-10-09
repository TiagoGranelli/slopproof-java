package com.example.slopproof;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * The layering rules that the compiler cannot see: which classes may reach the database, how
 * dependencies are injected, and what the generated packages hold.
 */
class ArchitectureTest {

  private static final String ROOT = "com.example.slopproof..";
  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.example.slopproof");

  @Test
  void controllersDependOnlyOnTheirFeatureTheContractAndTheFramework() {
    check(
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.example.slopproof.api..",
                "com.example.slopproof.posts..",
                "com.example.slopproof.shared..",
                "java..",
                "org.springframework..",
                "jakarta..",
                "org.jspecify..",
                "edu.umd.cs.findbugs..",
                "org.apache.."));
  }

  @Test
  void controllersNeverReachARepositoryOrJooq() {
    check(
        noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("org.jooq..")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("com.example.slopproof.db.."));
  }

  @Test
  void onlyRepositoriesUseTheJooqContext() {
    check(
        noClasses()
            .that()
            .haveSimpleNameNotEndingWith("Repository")
            .and()
            .resideInAPackage(ROOT)
            .and()
            .resideOutsideOfPackage("com.example.slopproof.db..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(DSLContext.class.getName()));
  }

  @Test
  void nothingUsesJdbcTemplate() {
    check(
        noClasses()
            .that()
            .resideInAPackage(ROOT)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate"));
  }

  @Test
  void injectsByConstructorNotByField() {
    check(fields().should().notBeAnnotatedWith(Autowired.class));
  }

  @Test
  void generatedPackagesHoldNoHandWrittenSourceExceptPackageInfo() throws IOException {
    // The build regenerates these packages, so a hand-written class there would be lost or would
    // hide a contract change. The class files cannot say where they were compiled from, so this
    // reads the source tree: only a package declaration may sit beside the generated code.
    for (String generated : List.of("api", "db")) {
      Path directory = Path.of("src/main/java/com/example/slopproof", generated);
      if (Files.isDirectory(directory)) {
        assertNoHandWrittenSource(directory);
      }
    }
  }

  private static void assertNoHandWrittenSource(Path directory) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      List<String> handWritten =
          files
              .filter(Files::isRegularFile)
              .filter(file -> !file.endsWith("package-info.java"))
              .map(Path::toString)
              .toList();
      assertTrue(
          handWritten.isEmpty(),
          () -> "write these in a feature package, not a generated one: " + handWritten);
    }
  }

  private static void check(ArchRule rule) {
    rule.check(CLASSES);
  }
}
