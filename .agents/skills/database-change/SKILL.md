---
name: database-change
description: Change the PostgreSQL schema of this repo with Flyway and jOOQ. Use when adding or changing a table, column, index or constraint, or when writing or reviewing a migration in src/main/resources/db/migration.
---

# Database change

Flyway migrations are the source of the schema. `generateJooq` runs them against an embedded PostgreSQL 18 during
the build and writes the jOOQ tables and records into `build/generated/jooq`, so a query that names a missing column
fails to compile.

## Steps

1. Add `src/main/resources/db/migration/V<n>__<slug>.sql`, where `<n>` is the next integer. Never rename or edit a
   migration that is already in the repo's history, because Flyway checks each file's checksum.
2. Write the SQL the way `V1__create_posts.sql` does. Set `lock_timeout` and `statement_timeout` at the top, so a
   migration never waits behind a lock or runs unbounded. Give a `uuid` id `DEFAULT uuidv7()`, which PostgreSQL 18
   provides, and a `timestamptz` column for every moment.
3. Run `./gradlew lintMigrations`. It runs squawk for PostgreSQL 18 with `config/squawk.toml`. A finding is a real
   migration hazard, such as a statement that takes a lock on a busy table or rewrites it. Rewrite the statement.
   Do not add a rule to `excluded_rules`, and do not suppress it inline.
4. Run `./gradlew generateJooq`, or `./gradlew compileJava`, which runs it. Then use the generated classes, such as
   `Tables.POSTS` and `PostsRecord`, in the repository.
5. Write the query in the feature's repository, the only class that touches the table. Bind every value with
   `eq(...)` on a typed column or `DSL.sql("... {0}", value)`. Never concatenate a value into SQL text: opengrep and
   ast-grep fail the build for it.
6. Test it against the embedded database through `support/EmbeddedPostgresConfiguration`, so the test runs the real
   migrations.

## Rules

- A migration already in the history is frozen. Change the schema by adding `V<n+1>`.
- Do not commit `build/generated/jooq`. It is regenerated on every build.
- A squawk warning is fixed in the SQL. This repo has no exception path for it, so a warning that seems wrong is a
  question for the lead, not a suppression.

## Done when

- `./gradlew lintMigrations` and `./gradlew check` pass.
- The migration file and the repository change are in one commit.
