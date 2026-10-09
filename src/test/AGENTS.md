# Tests

Read this before you add or change a test. The layers, the contract rule and the gates are below.

## Layers

| Class | Layer | Harness |
| --- | --- | --- |
| `PostCursorTest`, `SecretTest` | Unit | Plain JUnit 5 |
| `PostCursorPropertyTest` | Property | jqwik `@Property`, with `@Provide` generators |
| `ArchitectureTest` | Layering | ArchUnit over the compiled classes |
| `ModularityTest` | Modules | Spring Modulith `ApplicationModules.verify()` |
| `PostRepositoryTest` | Repository | `@SpringBootTest(webEnvironment = NONE)` on the shared database |
| `@ApiTest` classes | HTTP | The whole app through MockMvc, on the shared database |

Use the lowest layer that can show the behavior; `@ApiTest` only for HTTP status, headers or problem bodies.

## The contract rule

`ApiTest` adds `ContractValidationConfiguration`. Every exchange then goes through `ContractValidation`, which
checks against `api/openapi.yaml`: the response always, and the request unless it is marked. A test that sends
a request the contract refuses on purpose wraps it in `ContractValidation.refusedOnPurpose(...)`, so only the
response is checked, for example `?limit=0`.

Every error body is an RFC 9457 problem with `type`, `title` and `status`. Spring leaves `type` unset when a
problem comes from the framework itself, so `ProblemHandling` fills it in.

## The contract coverage gate

`ContractHits` appends `METHOD path status` to `build/contract-hits/<pid>.txt` whenever an `@ApiTest` exchange
reaches a documented handler. `ContractCoverageTest` reads those files and fails when an operation, or one of
its documented statuses, has no hit.

It runs in the `contractCoverage` task, after `test`. To pass it, add a test that reaches the missing response.
A new endpoint needs a 200 (or 201/204) test and a test for each 400 and 404 its contract lists.

`requireApiTests` fails `check` when the test classes lack `ContractCoverageTest`, so an empty test tree cannot
pass. JaCoCo skips its verification when no execution data exists, so that check is the one that covers it.

## Adding an API test

1. Annotate the class with `@ApiTest`, and use `MockMvc` (`mvc`) and `DSLContext` (`dsl`) for setup.
2. Clear the rows you use in `@BeforeEach`, for example `dsl.deleteFrom(POSTS).execute()`. The server is shared
   by the whole JVM, so a test isolates its own rows.
3. Do not start a database in a test. `SharedPostgres` owns the server, and `EmbeddedPostgresConfiguration`
   hands it to Spring.

## The shared database

`SharedPostgres` starts one embedded PostgreSQL 18 per test JVM on first use and stops it from a shutdown hook.
Each Gradle fork has its own server. The first start unpacks the PostgreSQL binaries into the temp directory,
so the first run on a machine is slow; antivirus scanning can slow it more.

## Running

- `./gradlew test --tests '*PostsApiTest'`: one class. Use a pattern, not a file path.
- `./gradlew test`: every test except the contract gate.
- `./gradlew contractCoverage`: `test`, then the gate.
- `./gradlew check`: everything, including the gates and `requireApiTests`.
- `./gradlew mutationTest`: PIT, with its thresholds. Slow, so it is not part of `check`.
