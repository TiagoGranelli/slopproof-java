package com.example.slopproof.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.sql.DataSource;

/**
 * One PostgreSQL 18 server for the whole test JVM. Starting a server takes seconds, and every
 * Spring context in the JVM would otherwise start its own, so the first use starts it and a
 * shutdown hook stops it. Tests isolate their data themselves: the server is shared, the rows are
 * not.
 */
final class SharedPostgres {

  private static final EmbeddedPostgres SERVER = start();

  private SharedPostgres() {}

  static DataSource dataSource() {
    return SERVER.getPostgresDatabase();
  }

  private static EmbeddedPostgres start() {
    try {
      EmbeddedPostgres server = EmbeddedPostgres.start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> close(server)));
      return server;
    } catch (IOException exception) {
      throw new UncheckedIOException("the embedded PostgreSQL did not start", exception);
    }
  }

  private static void close(EmbeddedPostgres server) {
    try {
      server.close();
    } catch (IOException exception) {
      throw new UncheckedIOException("the embedded PostgreSQL did not stop", exception);
    }
  }
}
