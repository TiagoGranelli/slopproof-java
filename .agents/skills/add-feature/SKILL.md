---
name: add-feature
description: Add a feature to this repo end to end, across the database, the API contract and a module. Use when a task adds a new resource, a new field a user can set, or a new user-facing operation, and the change needs a migration, an operation in api/openapi.yaml and a module package together.
---

# Add a feature

A feature is one package under `com.example.slopproof`, such as `posts`. Its module holds the controller, the service,
the repository and the domain records. Spring Modulith keeps the features apart: a module reaches another only through
its public types.

## Steps

1. **Contract.** Follow [api-change](../api-change/SKILL.md): the operations, the schemas and the problem responses in
   `api/openapi.yaml`, then `./gradlew compileJava`.
2. **Database.** Follow [database-change](../database-change/SKILL.md): a migration, then the generated jOOQ classes.
3. **Module package.** Create `com.example.slopproof.<feature>` with a `package-info.java` that says what the feature
   is, and `@NullMarked`:
   - an id as a record that wraps the `UUID` (`PostId`), so the compiler refuses another feature's id;
   - domain records for the values the service works with (`Post`);
   - a feature exception for each rule that can fail (`PostNotFoundException`), each mapped to its problem response;
   - a repository that holds every jOOQ query for the table;
   - a service with `@Transactional` on its public methods, and the rules;
   - a controller that implements the generated `XxxApi` and maps DTOs to the domain.
4. **Secrets.** A credential is a `Secret`, never a `String` field or parameter.
5. **Tests.** A unit test for each rule in the service. A repository test on the embedded database. A controller test
   with `MockMvc` through `@SpringBootTest`, which covers each status the contract declares.
6. **Modules.** Run the Spring Modulith verification test (`ApplicationModules.of(SlopproofApplication.class).verify()`).
   It fails when the feature reaches into another module's internals or when two modules depend on each other. Move
   the shared type to `shared`, or pass the value through a public type.

## Done when

- `./gradlew check` passes.
- `./gradlew bootTestRun` serves every new operation, and each one answers with its documented status.
- The contract, the migration, the module and its tests are in one commit, with `build/` left out.
