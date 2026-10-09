# 0001. Contract-first OpenAPI with generated interfaces

Status: accepted, 2026-10-09.

## Context

Agents write controllers and their request types by hand, and the contract drifts from the code without anyone
noticing. A hand-written DTO can accept a field the spec forbids, and a missing endpoint answers 404 until a client
finds it. The template needs one document that the server, the tests and the breaking-change check all read.

## Decision

`api/openapi.yaml` is the source. The openapi-generator `spring` target generates the server interfaces and DTOs
into `build/generated/openapi`, with `interfaceOnly` set: a controller implements each generated method, and a missing
method fails to compile. `vacuum` lints the spec with rules for the generated code (operation ids, problem responses,
limits). `oasdiff` fails the build on a breaking change against the merge-base.

## Consequences

- A controller cannot answer an operation the contract does not declare, and cannot drop one it does.
- A contract change regenerates code on the next `compileJava`. The generated code is never committed.
- Every string, array and object declares its limits, because the lint rules require them.
- A breaking change needs a new path, not an edit. The cost is a second path while clients move.
- The generator's output is excluded from Checkstyle, PMD and Error Prone by path, and from SpotBugs by package.
