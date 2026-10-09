---
name: api-change
description: Change the HTTP API contract of this repo. Use when adding or changing an endpoint, a request or response schema, a path parameter, or an error response in api/openapi.yaml, or a controller that implements the generated API.
---

# API change

`api/openapi.yaml` is the source of the server interfaces and DTOs. `./gradlew compileJava` generates them into
`build/generated/openapi`, so a controller cannot drift from the contract: it must implement every generated method.

## Steps

1. Edit `api/openapi.yaml`. Every operation has a camelCase `operationId`, which becomes the method name. Every
   4xx and 5xx response uses `application/problem+json` with the `Problem` schema. A path id is `format: uuid`. A
   string in a body or a query carries `maxLength`, an array carries `maxItems`, and an object sets
   `additionalProperties: false` and lists its `required` fields. `config/vacuum/ruleset.yaml` checks each of these.
2. Run `./gradlew compileJava`. It regenerates `com.example.slopproof.api` (the `XxxApi` interfaces) and
   `com.example.slopproof.api.model` (the `XxxDto` records). Never edit `build/generated`.
3. Implement the interface in the feature's controller. Map the DTOs to the domain and back in the controller, and
   let the service decide. Return the generated DTO type, never a domain record. A new operation with a 404 maps
   its feature exception to the problem response in `shared` (`ProblemHandling`), with a `type` such as
   `urn:slopproof:problem:post-not-found`.
4. Add the behaviour to a test. Each declared status gets a test that provokes it, because the contract's
   status codes are part of the API. Responses are validated against the spec in the test.
5. Run `./gradlew lintOpenApi` and `./gradlew checkApiBreaking` if you want the gates alone. `check` runs both.

## Breaking changes

`checkApiBreaking` (oasdiff) compares the spec with the merge-base of `origin/main` and fails on a breaking
change. Keep the change additive: a new operation, a new optional field, a new optional query parameter, a new
response status that a client can ignore.

A change that removes or renames a field, makes an optional field required, narrows a type or removes an operation
is breaking. Put it under a new path, such as `/api/v2/posts`, and keep the old path until clients have moved. The
gate then passes, because the old operation did not change.

## Done when

- `./gradlew check` passes, including `lintOpenApi` and `checkApiBreaking`.
- `api/openapi.yaml`, the controller and the test are in one commit. The generated code is not committed.
- For a change a client can reach: `./gradlew bootTestRun` serves the endpoint, and a request to it returns the
  documented status.
