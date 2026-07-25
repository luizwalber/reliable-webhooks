# ADR-0012: Contract-test tooling

## Status

Accepted. Settled in [wayfinder research ticket #16](https://github.com/luizwalber/reliable-webhooks/issues/16); full write-up at `.scratch/research-contract-test-tooling.md` (merged to `master`).

## Context

The brief calls for contract tests validating the backend against `openapi.yaml`, without naming a tool. Candidates considered: Spring Cloud Contract, `openapi4j`, Schemathesis/Dredd/Optic, and `atlassian/openapi-request-validator` (swagger-request-validator).

## Decision

Use **`atlassian/openapi-request-validator`** (a.k.a. swagger-request-validator), specifically the `swagger-request-validator-mockmvc` module.

Rationale:
- Spring Cloud Contract is the wrong shape — it's consumer-driven and owns its own Groovy/YAML contract format that *generates* stubs/specs, rather than validating requests/responses against an already-finalized `openapi.yaml`.
- `openapi4j` is dead (archived 2021).
- Schemathesis/Dredd/Optic are CLI/fuzzing tools that run outside `mvn test` and require a live server plus extra CI orchestration.
- swagger-request-validator is actively maintained (confirmed Spring 6 / Spring Boot 3 / Jakarta / JDK17+ compatibility, with Spring 7/Boot 4 support already landing), purpose-built to validate real HTTP request/response bodies against an existing OpenAPI spec, and ships a `ResultMatcher` that drops straight into existing `MockMvc` `.andExpect(...)` chains.
- It also has a WireMock module, reusable later for validating outbound webhook delivery payloads against a schema.

## Consequences

- No new test runner or CI infrastructure — contract assertions are just more `MockMvc` test methods.
- Contract tests validate directly against `openapi.yaml` ([ADR-0008](0008-openapi-resource-model.md)), so spec drift fails the build rather than silently diverging.
- This ADR's tooling choice is unaffected by [ADR-0014](0014-docker-compose-test-seam.md), which replaced Testcontainers-managed containers with a `docker compose`-provided Postgres/Redis for the same `MockMvc` tests — the container-orchestration mechanism changed, not the contract-validation approach.
- **Exception to "every test gets `openApi().isValid(...)`"**: the `swagger-request-validator-mockmvc` 2.42.0 `openApi()` matcher validates the request and response together, with no response-only mode. Tests that deliberately send a schema-invalid request (to assert our own bean validation rejects it with a 400) can never pass that combined check — so those specific tests assert the response shape via `jsonPath` instead, and skip the contract-validation assertion, with a comment explaining why.
