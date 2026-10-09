# Gates

`./gradlew check` (`gradlew.bat check` on Windows) runs every gate below. A change is done when it passes.
Each gate names the task that runs it, the config that sets its rules, and the fix for a failure. A `slopproof.<name>`
config is a convention plugin in `build-logic/src/main/kotlin/`.

Read this before you add an exception. The rule is the same for every gate: fix the code. Lower a threshold
only through a reviewed change to the config, never to make one build pass.

## Compile time

| Gate | Task | Catches | Config | Fix |
| --- | --- | --- | --- | --- |
| javac | `compileJava`, `compileTestJava` | Every javac warning (`-Xlint:all` with `-Werror`) | `slopproof.java-conventions` | Fix the warning. |
| Error Prone | `compileJava` | `UnusedVariable`, `CatchAndPrintStackTrace`, `EmptyCatch`, `SystemOut`, `ReturnValueIgnored`, `MissingOverride`, `FieldCanBeFinal`, `UnnecessaryLambda` (all errors) | `slopproof.static-analysis` | Fix the code. |
| NullAway | `compileJava` | Dereferenced nulls in `@NullMarked` packages (JSpecify mode) | `package-info.java` in each package | Mark the value `@Nullable` where it is null, or handle the null. |

Generated code under `build/generated` is skipped by Error Prone and by the Checkstyle and PMD tasks.

## Readability and slop smells

| Gate | Task | Catches | Config | Fix |
| --- | --- | --- | --- | --- |
| Checkstyle | `checkstyleMain`, `checkstyleTest` | Methods over 30 lines, files over 300 lines (comments count), more than 4 parameters, nested `if` or `try` deeper than two levels, cyclomatic complexity over 10, names `result`, `data`, `info`, `item`, `tmp`, `temp`, `obj`, `val`, star imports, `System.out`/`System.err`, `printStackTrace`, public types without Javadoc (main code only) | `config/checkstyle/checkstyle.xml` | Split the method or file by responsibility, rename the value, pass an options object. |
| PMD | `pmdMain`, `pmdTest` | Unused variables, parameters, fields and private methods, reassigned parameters, `printStackTrace`, empty catch, catching `Exception`, boolean returns that simplify, cognitive complexity over 15 | `config/pmd/ruleset.xml` | Delete the dead code, or return the expression directly. |
| CPD | `cpd` | A copy of 100 or more tokens | `slopproof.static-analysis` | Extract a method and call it from both places. The report lists the blocks in `build/reports/cpd/cpd.txt`. |

Checkstyle and CPD have no suppression mechanism in this repo. Fix the code.

## Bugs and security

| Gate | Task | Catches | Config | Fix |
| --- | --- | --- | --- | --- |
| SpotBugs (medium and up, effort max) | `spotbugsMain`, `spotbugsTest` | Bugs and FindSecBugs security findings. Skips the generated `api` and `db` packages. | `config/spotbugs/exclude.xml` | Fix the code, or add a reviewed `@SuppressFBWarnings` (see below). The annotation needs `spotbugs-annotations` on the compile classpath, which the posts code adds as `compileOnly`. |
| opengrep | `opengrepScan` | Request input reaching SQL, a process command, an outbound URL; secrets written to logs; `java.util.Random` for tokens; TLS checks switched off | `config/opengrep/rules/*.yaml` | Bind the value, validate it against an allowlist, or use `SecureRandom` and `Secret`. |
| ast-grep | `astGrepScan` | The 17 rules in `config/ast-grep/rules`: broad or empty catches, field injection, Lombok, legacy `java.util` dates, `Clock`-less `now()`, `Optional` fields or parameters, `Optional.get()`, SQL concatenation (JDBC and jOOQ), `String` secrets, `System.out`, `Thread.sleep` in main code, `@Transactional` in an `api` package or on a private method, `@SuppressWarnings` without a reason | `sgconfig.yml`, `config/ast-grep/rules` | Fix the code. Each rule's message says what to write instead. |
| gitleaks | `scanSecrets` | Keys and tokens in the working tree (default rules) | `config/gitleaks.toml` | Remove the value and rotate it. Fake values in `src/test/resources/fixtures/` are allowed. |

`opengrepScan` reads only files that git tracks. A new file that is not yet `git add`-ed is not scanned, so run
the gate after you stage the file.

## Tests and coverage

| Gate | Task | Catches | Config | Fix |
| --- | --- | --- | --- | --- |
| Tests | `test` | A failing test. Fails when test classes exist but JUnit finds none. | `slopproof.testing` | Fix the code or the test. |
| JaCoCo | `jacocoTestCoverageVerification` | Bundle line coverage below 80% or branch coverage below 70% | `slopproof.testing` | Add a test for the behaviour. Generated and entry-point classes are excluded. |
| Spotless | `spotlessCheck` | Formatting drift in Java (google-java-format 1.36.0), Kotlin and Gradle scripts (ktlint), and trailing whitespace or a missing final newline in `*.md`, `*.yaml`, `*.yml`, `*.sql`, `*.toml`, `*.xml` | `slopproof.format` | Run `spotlessApply`, review the diff, and commit it. |

`mutationTest` (PIT) is not part of `check`. It runs mutation testing over the non-generated classes and fails below
70% mutation score or 80% line coverage. Run it on CI or before a release, not on every change.

## Native CLI gates

These run as `externalChecks`, a dependency of `check`. Gradle downloads each tool once, checks its SHA-256 against
`gradle/native-tools.toml`, and runs it. Nothing is installed.

| Gate | Task | Catches | Config | Fix |
| --- | --- | --- | --- | --- |
| vacuum | `lintOpenApi` | Contract rules: every operation has a camelCase `operationId`; every 4xx and 5xx has a `application/problem+json` body; path ids are `uuid`; no open `additionalProperties`; request strings, query strings and arrays declare their limits | `config/vacuum/ruleset.yaml` (warnings fail too) | Fix `api/openapi.yaml`, then run `compileJava` to regenerate. |
| oasdiff | `checkApiBreaking` | A breaking change against the merge-base with `origin/main` (or `main`) | none (default rules, `--fail-on ERR`) | Make the change additive, or version the API. See [api-change](../../.agents/skills/api-change/SKILL.md). |
| squawk | `lintMigrations` | Migration hazards for PostgreSQL 18: long locks, unbounded statements, table rewrites | `config/squawk.toml` | Fix the SQL. Do not add a rule to `excluded_rules`. See [database-change](../../.agents/skills/database-change/SKILL.md). |
| ast-grep tests | `astGrepTest` | A rule that no longer matches its own valid and invalid snippets | `config/ast-grep/rule-tests` | Fix the rule or its snippets, together with the rule change. |
| osv-scanner | `scanVulnerabilities` | Known vulnerabilities in `gradle.lockfile` | `gradle.lockfile` | Upgrade the dependency. See [dependencies](dependencies.md). |

`scanVulnerabilities` logs `NOT RUN` and passes until `gradle.lockfile` exists.

### Skipping the native gates

`-Pslopproof.tools.skip=true` skips every native gate. The build still passes, and it logs `NOT RUN` for each one.
Use it only offline, and run the gates on a connected machine before you merge. Check the log for `NOT RUN`.

`-Pslopproof.tools.mirror=<url>` fetches the tools from an internal mirror of GitHub releases instead.

## Reviewed exceptions

An exception is allowed only where the table says so. Each one carries a reason in the code, and a reviewer reads it.

| Gate | Exception | Requirement |
| --- | --- | --- |
| javac, Error Prone, NullAway | `@SuppressWarnings("<Check>")` on the smallest element | A comment on the line above or on the same line says why. `suppress-warnings-needs-reason` fails without it. |
| SpotBugs | `@SuppressFBWarnings(value = "<Bug>", justification = "<why>")` | The `justification` names why the finding is safe here. |
| PMD | `@SuppressWarnings("PMD.<Rule>")` | The same reason comment as above. |
| opengrep | `// nosemgrep: <rule-id>` on the flagged line | A reason in the same comment. Verified to work for opengrep 1.30.2. |
| ast-grep | `// ast-grep-ignore: <rule-id>` on its own line directly above the flagged node | Supported by ast-grep 0.45.3. `config/sgconfig.yml` says exceptions belong in the rule, so prefer narrowing the rule with a `rule-tests` case. |
| gitleaks | None inline | A fake value goes under `src/test/resources/fixtures/`. Never allowlist a source path. |
| vacuum | `off` for a rule in `config/vacuum/ruleset.yaml` | A comment above the rule names the reason. The contract has no maps, so a typed map needs a reviewed exception. |
| squawk | None | Fix the SQL. |
| oasdiff | None | Version the API instead. |
| Checkstyle, CPD | None | Fix the code. |

Do not add `@SuppressWarnings` to make a warning go away without a reason. The ast-grep rule
`suppress-warnings-needs-reason` fails the build for it.
