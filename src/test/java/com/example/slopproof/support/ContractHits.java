package com.example.slopproof.support;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records which documented path and status each API exchange reached, so the contract coverage gate
 * can tell an untested operation. Each test JVM writes its own file, because Gradle runs several
 * forks in parallel.
 */
final class ContractHits {

  /**
   * The directory of hit files. Gradle sets it on the test tasks; without it nothing is recorded.
   */
  static final String DIRECTORY_PROPERTY = "slopproof.contractHits";

  private ContractHits() {}

  /** Appends {@code METHOD /path/template STATUS} for an exchange that reached a handler. */
  static void record(MvcResult exchange) throws IOException {
    String directory = System.getProperty(DIRECTORY_PROPERTY);
    Object template =
        exchange.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (directory == null || !(template instanceof String pattern)) {
      return;
    }
    String hit =
        "%s %s %d%n"
            .formatted(
                exchange.getRequest().getMethod(), pattern, exchange.getResponse().getStatus());
    Path directoryPath = Path.of(directory);
    Files.createDirectories(directoryPath);
    Files.writeString(
        directoryPath.resolve(ProcessHandle.current().pid() + ".txt"),
        hit,
        StandardCharsets.UTF_8,
        CREATE,
        APPEND);
  }

  /** Every line the test JVMs recorded in {@code directory}, or none when nothing ran. */
  static Set<String> recorded(Path directory) throws IOException {
    Set<String> hits = new HashSet<>();
    if (!Files.isDirectory(directory)) {
      return hits;
    }
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : files.toList()) {
        Files.readAllLines(file, StandardCharsets.UTF_8).forEach(hits::add);
      }
    }
    return hits;
  }
}
