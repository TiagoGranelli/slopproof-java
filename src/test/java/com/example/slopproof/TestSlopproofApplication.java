package com.example.slopproof;

import com.example.slopproof.support.EmbeddedPostgresConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the API on an embedded PostgreSQL for {@code ./gradlew bootTestRun}, with no Docker or
 * installed database. It is test code, so it never ships in the executable jar.
 */
public class TestSlopproofApplication {

  public static void main(String[] args) {
    SpringApplication.from(SlopproofApplication::main)
        .with(EmbeddedPostgresConfiguration.class)
        .run(args);
  }
}
