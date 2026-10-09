# Decisions

Each file records one architecture decision: its context, the choice, and what follows from it. Read the relevant
record before you reverse a choice. Add a new record, numbered in sequence, instead of editing an accepted one.

| ADR | Decision |
| --- | --- |
| [0001](0001-contract-first-openapi.md) | The OpenAPI contract is the source; server interfaces and DTOs are generated from it |
| [0002](0002-jooq-and-flyway.md) | Flyway migrations and jOOQ, not JPA |
| [0003](0003-embedded-postgres.md) | An embedded PostgreSQL 18 for tests and code generation, not Testcontainers |
| [0004](0004-native-tools-hash-pinned.md) | The gate CLIs are downloaded by Gradle and pinned by SHA-256, not installed with Node or Python |
| [0005](0005-gradle-over-maven.md) | Gradle, with locking, verification metadata and the configuration cache |
