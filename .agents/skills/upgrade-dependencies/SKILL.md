---
name: upgrade-dependencies
description: Upgrade a dependency, a Spring Boot BOM pin or a native CLI pin in this repo. Use when bumping any library or plugin version in gradle/libs.versions.toml, when a native tool in gradle/native-tools.toml changes, when a Renovate PR arrives, or when a lock or verification file must be rewritten.
---

# Upgrade dependencies

The policy is in [docs/agents/dependencies.md](../../../docs/agents/dependencies.md): exact pins, a one-day
quarantine, and the codegen pins that equal the Spring Boot BOM. Read it first. This skill is the procedure.

## Steps

1. **Pick the version and check its age.** Run the quarantine commands in `docs/agents/dependencies.md`. A version
   younger than one day is not pinned yet.
2. **Check the BOM.** If the bump is Spring Boot, read the BOM's `jooq.version`, `flyway.version` and
   `postgresql.version`. Set the catalog's `jooq`, `flyway` and `postgresql` to those values in the same change. For
   any other library the BOM manages, do not pin it in the catalog.
3. **Edit the catalog.** Change the exact version in `gradle/libs.versions.toml`. Keep the comment above a pin, and
   update it when the reason changes. A comment says why a pin is held back, for example Spotless and
   google-java-format.
4. **Rewrite the locks and checksums.** `./gradlew spotlessApply && ./gradlew --write-locks --write-verification-metadata
   sha256 check -x scanVulnerabilities` records the three lockfiles and a SHA-256 for each artifact in
   `gradle/verification-metadata.xml`. Review that the diff only adds and changes the bumped artifacts.
5. **Check on the new locks.** Run `./gradlew check`: it runs osv-scanner on the new lockfile, and a strict lock that
   does not match fails the build, which is the check you want. Also run `./gradlew mutationTest bootJar` once, so the
   tools outside `check` resolve against the checksums.
6. **Native tools.** For each changed tool in `gradle/native-tools.toml`:
   - take the release's `published_at` with `gh api repos/<owner>/<repo>/releases/tags/<tag> --jq .published_at`, and
     require it to be at least a day old;
   - download every platform asset in the table (`windows-x64`, `linux-x64`, `linux-arm64`, `macos-arm64`,
     `macos-x64`) from its `url`;
   - compute each SHA-256: `sha256sum <file>` on Linux and macOS, or
     `Get-FileHash -Algorithm SHA256 <file>` in PowerShell;
   - for an archive, check that `tar -tzf` (or `unzip -l`) lists the `entry` executable;
   - update `version`, `released`, `url`, `sha256` and `entry` for each platform.
   Then run `./gradlew externalChecks`. Gradle installs the new pin into its own directory and checks the hash.
7. **Smoke test.** For a Spring Boot, jOOQ, Flyway or OpenAPI generator bump, run `./gradlew bootTestRun` and call one
   endpoint. These bumps change generated code, so the tests alone are not enough.
8. **Open one PR per coupled family.** Spring Boot, the jOOQ and Flyway pair, the OpenAPI generator, and Error Prone with
   NullAway each go in their own PR.

## Done when

- `./gradlew check` passes.
- The catalog or native-tools change, `gradle/verification-metadata.xml` and the lockfiles are in one commit.
- The commit message names each version that moved and the BOM value it matches.
