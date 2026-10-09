# 0002. Flyway migrations and jOOQ, not JPA

Status: accepted, 2026-10-09.

## Context

JPA hides the SQL behind entities, lazy loading and a session that flushes on its own schedule. An agent writes a
mapping that loads the table on every read, and a reviewer reads the mapping rather than the query that runs. The
gates need to read the SQL: squawk lints each migration, and opengrep and ast-grep check each query for concatenation.

## Decision

Flyway migrations in `src/main/resources/db/migration` are the schema. `generateJooq` runs them against an embedded
PostgreSQL during the build and generates jOOQ tables and records into `build/generated/jooq`. A repository is the
only class that writes queries, and each query uses a typed column and bind values. The build has no JPA or Hibernate.

## Consequences

- A query that names a missing column or a wrong type fails to compile.
- Every schema change is a SQL file that squawk lints, and the query that uses it is a line of code a reviewer reads.
- There is no dirty checking and no lazy loading. A caller that needs a related row asks for it.
- More SQL is written by hand, and the team must know PostgreSQL to review it.
- A cold build starts a database to generate the classes. That costs seconds, and the build cache keeps it off warm runs.
