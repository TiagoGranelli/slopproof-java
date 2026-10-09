package com.example.slopproof.support;

import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Exposes the test JVM's PostgreSQL 18 server as the application's DataSource, so Flyway and jOOQ
 * run against a real database without Docker or an installed server. Tests import it; {@code
 * bootTestRun} uses it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfiguration {

  @Bean
  DataSource dataSource() {
    return SharedPostgres.dataSource();
  }
}
