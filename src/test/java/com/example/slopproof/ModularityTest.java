package com.example.slopproof;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Checks the module rules of {@link SlopproofApplication} with Spring Modulith. The generated api
 * and db packages are left out of the module model: Modulith would make each one a module, and
 * their types are generated, so no hand-written package-info can open them. ArchitectureTest guards
 * those packages instead.
 */
class ModularityTest {

  private static final DescribedPredicate<JavaClass> HANDWRITTEN =
      DescribedPredicate.not(
          JavaClass.Predicates.resideInAnyPackage(
              "com.example.slopproof.api..", "com.example.slopproof.db.."));

  @Test
  void keepsEveryModuleBehindItsPublicTypes() {
    ApplicationModules.of(SlopproofApplication.class, HANDWRITTEN).verify();
  }
}
