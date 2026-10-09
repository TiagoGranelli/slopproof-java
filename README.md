# slopproof-java

A Java 25 and Spring Boot 4.1.1 backend template for teams that let AI agents write code.

Agents write code fast, and a person cannot read every line of it. slopproof does not ask you to read more. It makes the
build prove what matters: the code keeps the API contract, follows the style the team chose, passes static analysis
and runs its tests against a real PostgreSQL. A change that breaks one of those fails `check` before a reviewer sees it.

It targets corporate Windows laptops without Docker, and Linux CI. The same `check` runs on both.

## Requirements

- JDK 25. If none is installed, Gradle downloads one from `api.foojay.io`.
- Git.
- Nothing else. No Docker, no Node, no Python, no WSL, no admin rights.

## Quick start

Windows (PowerShell):

```powershell
git clone <repository-url> slopproof-java
cd slopproof-java
.\gradlew.bat check
.\gradlew.bat installGitHooks
```

Linux and macOS:

```bash
git clone <repository-url> slopproof-java
cd slopproof-java
./gradlew check
./gradlew installGitHooks
```

`installGitHooks` points git at `config/git-hooks`, so the pre-commit hook refuses a commit that is not formatted. Run
it once per clone.

## What `check` runs

`./gradlew check` runs every gate. A run with nothing changed takes under a second. A run that reruns every task
(`--rerun-tasks --no-build-cache`) took 12 seconds on the skeleton, with the tools and dependencies already cached. The
first run on a new machine also downloads them. Per-gate times are not measured here.

| Gate | Catches |
| --- | --- |
| Compile with `-Werror`, Error Prone, NullAway | Warnings, unused code, swallowed errors, nulls |
| Checkstyle, PMD, CPD | Long methods and files, bad names, deep nesting, copy-paste |
| SpotBugs with FindSecBugs | Bugs and security findings |
| Spotless | Formatting drift, and whitespace in Markdown, YAML, SQL and TOML |
| Tests with JaCoCo | Failing tests, and coverage below 80% lines or 70% branches |
| vacuum | Contract rules: operation ids, problem responses, limits |
| oasdiff | Breaking changes against `origin/main` |
| squawk | Migration hazards on PostgreSQL 18 |
| ast-grep and opengrep | Slop patterns and insecure code |
| gitleaks | Secrets in the working tree |
| osv-scanner | Known vulnerabilities in the locked dependencies |

The full list, with config files, fixes and the rules for an exception, is in
[docs/agents/gates.md](docs/agents/gates.md).

## Run it locally

```bash
./gradlew bootTestRun
```

This starts the API on port 8080 (the Spring Boot default), backed by an embedded PostgreSQL 18 that the run starts and
stops. No database server is installed.

`./gradlew mutationTest` runs mutation testing. It is slow, so it runs on CI and before a release, not on every change.

## Corporate networks

- **Proxy.** Put `systemProp.https.proxyHost=<host>` and `systemProp.https.proxyPort=<port>` in
  `~/.gradle/gradle.properties`.
- **No access to `api.foojay.io`.** Install Temurin 25 and point `org.gradle.java.installations.paths` at it.
- **No access to GitHub.** Pass `-Pslopproof.tools.mirror=<url>` to an internal copy of the release assets.
- **Offline.** Pass `-Pslopproof.tools.skip=true`. The native gates are skipped and the log says `NOT RUN`. Run them on
  a connected machine before you merge.

[AGENTS.md](AGENTS.md) has the rest: the first start of the embedded database, antivirus, and line endings.

## Layout

```
api/openapi.yaml              the API contract: the source of the server interfaces and DTOs
src/main/java/.../posts       a feature: controller, service, repository, records
src/main/java/.../shared      plumbing shared by the features
src/main/resources/db/migration   Flyway migrations: the source of the schema
src/test/java                 tests, in the packages they cover
config/                       the rules of each gate
build-logic/                  convention plugins that wire the gates
gradle/                       the version catalog, the native tool pins and the wrapper
docs/                         the reference for people and agents, and the decision records
.agents/skills/               the step-by-step skills for the common changes
```

## For agents

[AGENTS.md](AGENTS.md) is the entry point. `CLAUDE.md` imports it, so Claude Code reads the same file. The skills in
`.agents/skills/` describe the common changes. Claude Code finds them in `.claude/skills/`, where each file is a
pointer to the skill in `.agents/skills/`. The pointers are plain files, not a symlink: without `core.symlinks`, Git
on Windows checks a symlink out as a text file, and Claude Code then finds no skills. Keep each pointer's
`description` equal to its skill's.

## Decisions

The reasons behind the main choices are in [docs/decisions](docs/decisions/README.md): the contract-first API, jOOQ and
Flyway, the embedded PostgreSQL, the hash-pinned gate tools, and Gradle.

## License

MIT. See [LICENSE](LICENSE).
