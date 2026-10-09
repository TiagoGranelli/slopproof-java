package com.example.slopproof.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Starts a PostgreSQL 18 server in this JVM and exposes it as the application's DataSource, so
 * Flyway and jOOQ run against a real database without Docker or an installed server. Tests import
 * it; {@code bootTestRun} uses it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfiguration {

  @Bean(destroyMethod = "close")
  EmbeddedPostgres embeddedPostgres() throws IOException {
    return EmbeddedPostgres.start();
  }

  @Bean
  DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
    return embeddedPostgres.getPostgresDatabase();
  }
}
