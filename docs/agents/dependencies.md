# Dependencies

The procedure for a bump is in the [upgrade-dependencies](../../.agents/skills/upgrade-dependencies/SKILL.md) skill.
This page holds the policy and the commands that check it.

## Policy

- **Exact pins.** Every version is exact in `gradle/libs.versions.toml` (Gradle libraries and plugins) and
  `gradle/native-tools.toml` (native CLIs). No ranges, no `+`, no `latest`.
- **Spring Boot manages most libraries.** The `spring-boot` version in the catalog selects a BOM, and the BOM pins
  Jackson, Mockito, JUnit and the rest of the Spring stack. Let it manage those. Do not pin them in the catalog.
- **The codegen pins equal the BOM.** jOOQ, Flyway and the PostgreSQL driver are used by build-time code generation in
  `build-logic`, which runs outside the BOM. Their catalog versions must equal the values in the BOM, or the generated
  code and the runtime disagree. Check the BOM's `jooq.version`, `flyway.version` and `postgresql.version`.
- **Quarantine.** A version is pinned only when it is at least one day old, so a broken release has time to be
  withdrawn. Renovate waits seven days for an ordinary update and one day for a vulnerability fix (`renovate.json`).
- **One PR per coupled family.** Spring Boot, the jOOQ and Flyway pair, the OpenAPI generator, and Error Prone with
  NullAway each change in their own PR, because each changes generated or analysed code.
- **Locks and verification.** Once the supply-chain work lands, the dependency locks are STRICT: a resolution that
  differs from `gradle.lockfile` fails the build. `gradle/verification-metadata.xml` pins a SHA-256 for every artifact.
  Both are rewritten by commands, never by hand.

## Checking the quarantine

The release date of a Maven artifact is the upload time that Maven Central sends in the `Last-Modified` header of its
POM, so the POM alone answers the question.

```bash
# Gradle library: the POM for jOOQ 3.21.7, then the release date
curl -sI https://repo1.maven.org/maven2/org/jooq/jooq/3.21.7/jooq-3.21.7.pom | grep -i '^last-modified'

# Newest versions Central lists for a library
curl -s https://repo1.maven.org/maven2/org/jooq/jooq/maven-metadata.xml | grep -o '<release>[^<]*'

# Gradle plugin: its marker artifact on the plugin portal
curl -sI https://plugins.gradle.org/m2/org/springframework/boot/org.springframework.boot.gradle.plugin/4.1.1/org.springframework.boot.gradle.plugin-4.1.1.pom | grep -i '^last-modified'

# Native tool: the GitHub release date (the gh CLI must be signed in)
gh api repos/daveshanley/vacuum/releases/tags/v0.32.0 --jq .published_at
```

PowerShell has no `grep`. Use `Select-String` instead, for example
`curl.exe -sI <url> | Select-String -Pattern '^last-modified'`.

The Spring Boot BOM's codegen versions:

```bash
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom \
  | grep -E '<(jooq|flyway|postgresql)\.version>'
```

A newer release can exist on Central and still not be the BOM's version. jOOQ 3.21.9 is on Central, for example, but
the BOM for Spring Boot 4.1.1 pins 3.21.7. Then the catalog stays at 3.21.7 until the Spring Boot bump.

## Native tools

Each entry in `gradle/native-tools.toml` names a release and a SHA-256 for each platform. Gradle refuses an asset whose
hash differs from the pin. A pin change needs the new hash of every platform asset, which the upgrade skill covers.
The tools are vacuum, oasdiff, squawk, ast-grep, opengrep, gitleaks and osv-scanner.
