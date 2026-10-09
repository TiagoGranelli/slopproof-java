package com.example.slopproof.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fails when a documented operation, or one of its documented statuses, is reached by no API test.
 * It reads the hits that the API tests recorded, so it runs after them: {@code ./gradlew check}
 * wires that order through the {@code contractCoverage} task.
 */
class ContractCoverageTest {

  @Test
  void everyDocumentedStatusOfEveryOperationIsReached() throws IOException {
    String directory = System.getProperty(ContractHits.DIRECTORY_PROPERTY);
    assertNotNull(directory, "run this through ./gradlew contractCoverage, which records the hits");
    OpenAPI contract = new OpenAPIV3Parser().read(ContractValidation.SPECIFICATION);
    assertNotNull(contract, "the contract " + ContractValidation.SPECIFICATION + " does not parse");
    Set<String> reached = ContractHits.recorded(Path.of(directory));

    List<String> unreached = new ArrayList<>();
    contract
        .getPaths()
        .forEach((path, pathItem) -> collectUnreached(path, pathItem, reached, unreached));

    assertTrue(
        unreached.isEmpty(),
        () ->
            "no API test reaches these documented responses:\n  " + String.join("\n  ", unreached));
  }

  private static void collectUnreached(
      String path, PathItem pathItem, Set<String> reached, List<String> unreached) {
    pathItem
        .readOperationsMap()
        .forEach(
            (method, operation) -> {
              for (String status : operation.getResponses().keySet()) {
                String hit = "%s %s %s".formatted(method, path, status);
                if (!reached.contains(hit)) {
                  unreached.add(describe(operation, method.name(), path, status));
                }
              }
            });
  }

  private static String describe(Operation operation, String method, String path, String status) {
    return "%s: %s %s %s".formatted(operation.getOperationId(), method, path, status);
  }
}
