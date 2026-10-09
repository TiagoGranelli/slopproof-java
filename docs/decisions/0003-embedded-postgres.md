# 0003. Embedded PostgreSQL instead of Testcontainers

Status: accepted, 2026-10-09.

## Context

The target laptops are corporate Windows machines without Docker, and an agent cannot install it. Testcontainers
needs a container runtime for every test and for the jOOQ code generation. Linux CI could run it, but the tests would
then differ from the laptop, and a test that passes only on CI does not help the person who wrote it.

## Decision

The `io.zonky.test:embedded-postgres` library starts real PostgreSQL 18 server processes from binaries that Maven
delivers. Code generation, the tests and `bootTestRun` all use it. The binaries for Windows, Linux (glibc and Alpine)
and Intel macOS stay on the classpath, so the lockfile and the verification metadata are the same on every platform.

## Consequences

- No Docker, WSL or admin rights are needed. Windows, Linux and macOS run the same database.
- The database is PostgreSQL 18, the version the schema targets (`uuidv7()` is built in).
- The first start extracts the binaries to a temporary directory. Antivirus scanning can make that first start slow, so
  the startup wait is one minute.
- A service that is not PostgreSQL (Redis, a message broker) needs its own embedded or in-process answer. This record
  does not cover it.
