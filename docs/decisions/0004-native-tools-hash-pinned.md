# 0004. Gate CLIs downloaded and hash-pinned by Gradle

Status: accepted, 2026-10-09.

## Context

The gates use mature command-line tools: vacuum (OpenAPI lint), oasdiff (breaking changes), squawk (migration lint),
ast-grep, opengrep, gitleaks and osv-scanner. The TypeScript template runs its equivalents through Node and Python,
and a corporate Windows laptop often has neither, and no admin rights to install them.

## Decision

`gradle/native-tools.toml` pins each tool's release, and a SHA-256 for each platform's asset. Gradle downloads the
asset on first use into the Gradle user home, checks the hash, extracts it and runs it. The `native-tools` gates
(`externalChecks`) run it over the project. Two options cover the rest: `-Pslopproof.tools.mirror` fetches from an
internal copy of the releases, and `-Pslopproof.tools.skip=true` skips every gate and says so in the log.

## Consequences

- Nothing is installed system-wide, and no Node, Python or WSL is needed.
- A tool bump is a reviewed change of a hash, and the hash must match the asset before the tool runs.
- A tool with no asset for a platform fails by name, so a missing platform is visible.
- An offline laptop must skip the gates. Its build passes with `NOT RUN` logged, so the gates must run on a connected
  machine before a merge.
- The gates depend on GitHub releases being reachable or mirrored.
