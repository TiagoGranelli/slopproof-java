# AGENTS.md

slopproof-java is a Java 25 and Spring Boot 4.1.1 backend template. Its point is to stop agent slop with gates that
run without a person reading the diff: the contract, the code style, the static analysis and the tests must agree.
It is the Java sibling of the TypeScript template `proofstack`.

A change is done when `./gradlew check` passes. On Windows, run `.\gradlew.bat check`. A change to the contract or the
database is also done when `./gradlew bootTestRun` serves the changed endpoint. Run `./gradlew spotlessApply` before you
commit. After `./gradlew installGitHooks`, the pre-commit hook refuses a commit that fails `spotlessCheck`.

Read [docs/agents/gates.md](docs/agents/gates.md) before you change code that a gate rejects. It says what each gate
catches, its config, the fix, and the only exceptions allowed.

## Architecture map

| Path | Role | Who may use it |
| --- | --- | --- |
| `api/openapi.yaml` | The contract: operations, schemas and problem responses. The source of the server code | Edit it by hand |
| `build/generated/openapi` (`com.example.slopproof.api`, `.api.model`) | Generated `XxxApi` interfaces and `XxxDto` records | Never edit. Regenerate with `compileJava` |
| `src/main/resources/db/migration/V<n>__*.sql` | Flyway migrations, the source of the schema | Add a new file. Never edit an applied one |
| `build/generated/jooq` (`com.example.slopproof.db`) | Generated jOOQ tables and records for the migrations | Never edit. Regenerate with `compileJava` |
| `com.example.slopproof.<module>` (for example `posts`) | One feature: controller, service, repository, domain records | Its own package; `shared`; the generated `api` and `db` |
| `com.example.slopproof.shared` | Plumbing with no feature knowledge: `Secret`, problem responses, JSON input | Nothing in a module |
| `com.example.slopproof` | `SlopproofApplication` and the `@NullMarked` root `package-info.java` | Everything |
| `src/test/java` | Tests, in the packages they cover; `support/` holds the embedded database | Everything |
| `config/` | Rules for every gate: checkstyle, PMD, SpotBugs, opengrep, ast-grep, vacuum, squawk, gitleaks | Edit only through a reviewed change |
| `build-logic/` | The convention plugins that wire the gates: `slopproof.<name>.gradle.kts` | Edit with `./gradlew check` |

Module rules, as Spring Modulith checks them: a module is a direct sub-package of `com.example.slopproof`. Another
module reaches it only through its public types, never its internals. `api` and `db` are generated, so no module
owns them. `shared` is the one common module. Modulith's `ApplicationModules.verify()` enforces these in the test suite.

## Where code goes

- **A new endpoint.** Edit `api/openapi.yaml` first, then run `./gradlew compileJava`. The controller in the feature's
  package implements the generated interface. Do not add `@RequestMapping` or other mapping annotations by hand.
- **Business rules and transactions.** A service class in the feature package. Its public methods carry
  `@Transactional`. The controller calls the service, never the repository.
- **Queries.** The repository is the only class that touches a table. It uses jOOQ typed columns and bind values.
- **A domain error.** Throw a feature exception. `shared` maps it to `application/problem+json` in one place.
- **A credential.** Hold it in `Secret`. A `String` named like a password, token or key fails the build.
- **A cross-cutting type.** `shared`. A feature never imports another feature's internals.
- **A table.** A migration, then `compileJava` generates its jOOQ classes. See [database-change](.agents/skills/database-change/SKILL.md).

Workflows: each multi-step change has a skill in `.agents/skills/`. Load it before you start.

| Task | Skill |
| --- | --- |
| An endpoint, schema, or problem response in `api/openapi.yaml` | [api-change](.agents/skills/api-change/SKILL.md) |
| A table, column, index or migration | [database-change](.agents/skills/database-change/SKILL.md) |
| A feature across the database, the contract and a module | [add-feature](.agents/skills/add-feature/SKILL.md) |
| A dependency, a BOM pin, a native tool pin or a lock | [upgrade-dependencies](.agents/skills/upgrade-dependencies/SKILL.md) |

## Code style (enforced)

Checkstyle, PMD, Error Prone and ast-grep enforce these. Their configs are the reference, and the limits are not
negotiable: fix the code, never the threshold.

- **Size.** Methods up to 30 lines, files up to 300 lines, and comments count toward the file limit. More than 4
  parameters goes in a record or an options object. Split by responsibility, not by moving lines.
- **Nesting.** At most two nested `if` or `try` blocks. Return early. Cyclomatic complexity is 10 at most
  (`config/checkstyle/checkstyle.xml`, `config/pmd/ruleset.xml`).
- **Names.** Never `result`, `data`, `info`, `item`, `tmp`, `temp`, `obj` or `val`: name the value so `rg` finds its
  definition. No star imports.
- **Nullability.** Every package has `@NullMarked` in its `package-info.java`. Mark a nullable value `@Nullable`
  (JSpecify). NullAway fails the build on a dereference it cannot prove safe.
- **Types.** Records for values. No `Optional` as a field or parameter: return it, or use a nullable parameter. No
  Lombok, no `java.util.Date` or `Calendar`, and no `now()` without a `Clock`.
- **Injection.** Constructor parameters into `private final` fields. No `@Autowired` on a field.
- **Errors.** No empty catch. No `catch (Exception)`, `Throwable` or `RuntimeException` unless it rethrows with the
  cause. No `printStackTrace`.
- **Logging.** SLF4J (`LoggerFactory.getLogger`). No `System.out` or `System.err`. Never log a `Secret`, a password or a
  request body.
- **SQL.** Bind every value: `DSL.sql("... = {0}", value)` or a typed column. Never concatenate text into a query.
- **Transactions.** `@Transactional` on a public method of a Spring bean only. Never on a controller, never on a private
  method. Those fail the build.
- **Javadoc.** Every public type gets a sentence of intent. Comments say why, not what.
- **Format.** google-java-format, two-space indent, LF line endings. `spotlessApply` writes it.

## Verify

| Command | Covers |
| --- | --- |
| `./gradlew check` | Everything: compile with `-Werror`, Error Prone, NullAway, Checkstyle, PMD, CPD, SpotBugs, Spotless, tests with JaCoCo, and every native gate |
| `./gradlew test` | Tests only (JUnit 5, jqwik properties, ArchUnit) |
| `./gradlew externalChecks` | The native CLI gates only: vacuum, oasdiff, squawk, ast-grep, opengrep, gitleaks, osv-scanner |
| `./gradlew spotlessApply` | Formats the code in place. Run it before you commit |
| `./gradlew bootTestRun` | Runs the API on an embedded PostgreSQL 18. Needs no Docker and no installed database |
| `./gradlew mutationTest` | PIT mutation testing, with its thresholds. Slow: run it on CI or nightly, not per change |
| `./gradlew openApiGenerate`, `./gradlew generateJooq` | Regenerate the code from the contract and the migrations. `compileJava` runs both |
| `./gradlew installGitHooks` | Points git at `config/git-hooks`, so the pre-commit hook runs `spotlessCheck`. Once per clone |
| `./gradlew resolveAndLockAll --write-locks` | Rewrites `gradle.lockfile`. Only after a dependency change (wp/supply-chain) |
| `./gradlew --write-verification-metadata sha256 check` | Rewrites `gradle/verification-metadata.xml` with checksums (wp/supply-chain) |
| `./gradlew cyclonedxDirectBom` | Writes the SBOM to `build/reports/sbom/bom.json` (wp/supply-chain) |
| `./gradlew checkLicense` | Fails on a license that is not in `config/licenses/allowed-licenses.json` (wp/supply-chain) |

`check` takes about 12 seconds from cold on the skeleton and under a second when nothing changed.

## Generated and locked files

Never edit these by hand. Claude Code refuses the edit (`.claude/settings.json`).

- `build/**`: generated code, reports and the native tool markers. Regenerate with the build.
- `gradle.lockfile`: `./gradlew resolveAndLockAll --write-locks`.
- `gradle/verification-metadata.xml`: `./gradlew --write-verification-metadata sha256 check`.
- `gradle/wrapper/**`: `./gradlew wrapper --gradle-version <version>`, reviewed as a build-tool change.
- A migration already in `src/main/resources/db/migration`: Flyway stores its checksum, so a changed file fails
  validation. Add a new `V<n>__` file instead.

## Dependencies and the version policy

Every version is pinned exactly in `gradle/libs.versions.toml` and `gradle/native-tools.toml`. Ranges and `+` are not
allowed. The Spring Boot BOM manages most libraries, so a Spring Boot bump moves them. jOOQ, Flyway and PostgreSQL
are pinned in the catalog too, because the code generator runs outside the BOM: their catalog versions must equal the
BOM's. A new release waits one day before it is pinned, and Renovate waits seven (one for a vulnerability fix). The
policy and the quarantine check are in [docs/agents/dependencies.md](docs/agents/dependencies.md).

## Windows and corporate networks

The target laptops run Windows without Docker, WSL or admin rights. Nothing here needs them.

- **JDK 25.** Gradle downloads a JDK through `api.foojay.io` when none is installed. If that host is blocked, install
  Temurin 25 and add it to `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
- **Proxy.** Put `systemProp.https.proxyHost=<host>` and `systemProp.https.proxyPort=<port>` in
  `~/.gradle/gradle.properties`. The native tool downloader reads the same settings, or `HTTPS_PROXY=host:port`. Never
  put credentials in a variable: the downloader refuses them.
- **Mirrors.** `-Pslopproof.tools.mirror=<url>` fetches the native tools from an internal copy of GitHub releases.
  Maven Central is the only repository. A corporate Maven proxy is a reviewed change to `settings.gradle.kts`.
- **Offline.** `-Pslopproof.tools.skip=true` skips every native gate and logs `NOT RUN`. The build still passes, so
  run the gates on a connected machine before you merge.
- **First run.** The embedded PostgreSQL extracts its binaries to a temporary directory. Antivirus scans can slow that
  first start, and the wait is one minute.
- **Line endings.** The repo is LF (`.gitattributes`, `eol=lf`), and Spotless writes LF. An editor that saves CRLF makes
  `spotlessCheck` fail on that file, so run `spotlessApply`.

## Sharp edges

- **google-java-format is pinned at 1.36.0.** Spotless 8.10.3 fails with `NoSuchMethodError` on 1.37. Check
  Spotless before you bump it.
- **`-Werror` is on.** Any javac warning fails the build, including a deprecation from Spring 7. Fix the warning. Do
  not add `-Xlint:-<name>`.
- **NullAway checks only `@NullMarked` packages.** A new package without a `package-info.java` is not checked for nulls,
  and no gate reports it.
- **Checkstyle counts comment lines** toward the 300-line file limit, and `maxWarnings` is 0.
- **Generated packages.** Checkstyle, PMD and Error Prone skip `build/generated` by path. SpotBugs skips the `api` and
  `db` packages by name. Never hand-write code in those packages: it is exempt from SpotBugs, and regeneration removes
  whatever sits in `build/generated`. Write it in a feature package.
- **opengrep scans only git-tracked files.** `git add` a new file before you rely on `opengrepScan`.
- **PIT has no incremental history.** The open-source `pitest` 1.30 cannot store history. Every `mutationTest` is a full run.
- **The configuration cache is on.** A task that reads the project at execution time needs
  `notCompatibleWithConfigurationCache`, as the license plugin does.
- **Native tools are cached per pin.** A changed SHA-256 in `native-tools.toml` installs into a new directory, so the
  first run after the change downloads it.
- **Each test fork starts its own PostgreSQL.** The fork count is half the cores, so a laptop runs a few at once.
- **Mockito runs as a Java agent**, so JDK 25 does not warn about it. Keep that argument in `slopproof.testing`.

## Tests

Tests live in `src/test/java`, in the packages they cover. Unit tests use JUnit 5, and properties use jqwik. ArchUnit
rules are tests too. An HTTP test runs the app on the embedded database through `support/EmbeddedPostgresConfiguration`,
never a mock of the database. Each response is validated against `api/openapi.yaml`, so a response that drifts from the
contract fails. Test classes need no Javadoc; every other Checkstyle rule still applies to them.

The `test` task fails when test classes exist and JUnit discovers none. It does not catch one JUnit 4 class among
Jupiter tests, so write every test with JUnit 5.

## Library docs

- **Spring Boot 4 and Spring Modulith:** the reference docs at `docs.spring.io`. The BOM version is in
  `gradle/libs.versions.toml`.
- **jOOQ and Flyway:** the reference docs for the pinned versions. The generated sources are under `build/generated/jooq`.
- **openapi-generator (Spring, Jakarta, Jackson 3):** the options are in `build-logic/.../slopproof.openapi.gradle.kts`.
- **Native tools:** each tool's own documentation. The pinned release is in `gradle/native-tools.toml`.
- **Decisions:** [docs/decisions](docs/decisions/README.md). Read the relevant ADR before you reverse a choice.
