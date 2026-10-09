# 0005. Gradle over Maven

Status: accepted, 2026-10-09.

## Context

The template needs strict dependency locking, checksum verification of every artifact, a configuration cache so a warm
`check` takes about a second, and custom gate tasks that share one convention. Maven has no built-in strict lock or
checksum verification. Its plugin model shares convention code less easily than Gradle's included builds.

## Decision

Gradle 9.8 with the wrapper committed. The version catalog `gradle/libs.versions.toml` holds every pin. An included
build, `build-logic`, holds the convention plugins that wire each gate, so the root build reads like a list of gates.

## Consequences

- Dependency locks are STRICT and checksums are in `gradle/verification-metadata.xml`, once the supply-chain work lands.
- The configuration cache is on. A plugin that reads the project at execution time must opt out, as the license
  report plugin does.
- `build-logic` has its own classpath, and it is locked too. Its lock is an open item (see the upgrade skill).
- Contributors need the Gradle wrapper, not a Gradle install. Gradle upgrades go through the wrapper, reviewed as
  build-tool changes.
