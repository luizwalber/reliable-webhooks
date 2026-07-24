# ADR-0008: OpenAPI resource model and API surface

## Status

Accepted. Drafted in [wayfinder ticket #8](https://github.com/luizwalber/reliable-webhooks/issues/8), finalized by [#10](https://github.com/luizwalber/reliable-webhooks/issues/10) and [#14](https://github.com/luizwalber/reliable-webhooks/issues/14). Lives at `openapi.yaml` (repo root), merged to `master`.

## Context

The brief's "contrato antes de código" rule requires the API contract settled before implementation. This ticket is where the four preceding decisions (fan-out/Delivery resource, idempotency, retry topology, HMAC) get assembled into one concrete, versioned OpenAPI document — plus the choices that don't belong to any single decision above (pagination, error format, versioning, document structure).

## Decision

- **One document**, tag-separated into `Ingestion` (producer-facing) and `Management` (frontend-facing) operations.
- **Four resource types**: `Event` (read-only), `Endpoint` (CRUD, secret returned once — [ADR-0007](0007-hmac-signing.md)), `Delivery` (one per Event×Endpoint pair, carries the state machine — [ADR-0001](0001-fan-out-and-delivery-resource.md), [ADR-0005](0005-state-machine.md)), `Attempt` (individual HTTP tries, nested under a Delivery). No separate DLQ resource: `GET /v1/deliveries?state=DEAD` is the DLQ view, `POST /v1/deliveries/{id}/retry` is manual retry ([ADR-0005](0005-state-machine.md)).
- **Pagination**: offset-based `page`/`size`, matching Spring Data's `Pageable`/`Page<T>` directly — no cursor pagination to hand-roll.
- **Errors**: RFC 7807 Problem Details via Spring Boot 3's built-in `ProblemDetail` — no custom error envelope.
- **Versioning**: explicit `/v1` path prefix on every route.
- **Runtime correction**: the project's Java version is **21**, not the brief's originally stated 17 — corrected here and propagated to every later reference (Docker Compose, build tooling).
- **Deliberately deferred** (not silently invented): a metrics/success-rate endpoint and a simulated-consumer config endpoint — both were still open questions at draft time, added later once [ADR-0009](0009-metrics.md) and [ADR-0010](0010-simulated-consumers.md) settled them (metrics landed as embedded Endpoint fields, not a new endpoint; simulated consumers needed no endpoint changes at all).
- **Inbound API auth** (producer→ingest, frontend→management) is intentionally absent from the spec — out of scope for this project (see `CONTEXT.md`); only outbound HMAC signing is in scope.

## Consequences

- `openapi.yaml` is the single source of truth going forward — frontend and contract tests ([ADR-0012](0012-contract-test-tooling.md)) both consume it directly, no drift-prone hand-maintained duplicate docs.
- Every later resource-shape decision (metrics fields, simulator) had to fit into this four-resource model rather than inventing new top-level resources — kept the surface small.
