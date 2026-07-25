# ADR-0013: Clean Architecture layering for the backend

## Status

Accepted.

## Context

The backend (`back_java/`) started as a package-by-feature layout (`event`, `endpoint`, `delivery`, `outbox`, `idempotency`, each a flat package mixing JPA entities, Spring Data repositories, business logic, and `@RestController`s together). As the system grows into the harder slices — outbox poller, delivery workers, retry/circuit-breaker logic — that flat shape makes it increasingly hard to see or enforce which code is a business rule versus which is a framework/persistence/HTTP detail, and to unit-test business logic without a database or web layer.

## Decision

Each bounded module carries four layers as subpackages, dependencies pointing inward only:

```
presentation → application → domain ← infrastructure
```

- **domain** — entities, value objects, state-machine rules, repository/gateway ports (interfaces). No Spring, JPA, Jackson, or HTTP.
- **application** — one use-case class per user-facing operation (e.g. `IngestEventUseCase`, `RegisterEndpointUseCase`), taking domain-level commands and returning domain-level views. No JPA entities, no `@RestController`.
- **infrastructure** — JPA entities, Spring Data repositories, adapters implementing the domain's repository ports, mappers between JPA entities and domain entities.
- **presentation** — `@RestController`s, HTTP request/response DTOs, the global `ProblemDetail` advice, mappers between use-case output and HTTP DTOs.

Full rule set and package-naming conventions: `.claude/clean-architecture.mdc`.

A cross-cutting technical concern with no domain rules of its own (the Redis-backed idempotency cache, pagination helpers, the exception advice) is infrastructure or presentation, not domain — it doesn't earn a full four-layer module.

Two pragmatic exceptions, made deliberately rather than by omission:
- Domain repository ports use Spring Data's `Page`/`Pageable` types directly rather than inventing a framework-agnostic pagination abstraction — treated as a generic contract, not an ORM or HTTP concern.
- `EventJpaEntity`/`DeliveryJpaEntity`/`EndpointJpaEntity` reuse the domain's state enums (`EventState`, `DeliveryState`, `CircuitBreakerState`) directly via `@Enumerated(EnumType.STRING)` rather than mapping to parallel infrastructure-level enums — an enum is just data, and duplicating it bought no isolation.

## Consequences

- Every use case is testable with plain-Java fakes of its domain ports, no Spring context or database required (not yet exercised by a test in this slice, but now structurally possible).
- Persistence and HTTP concerns can change independently of business rules: swapping the ORM or the web framework touches only `infrastructure`/`presentation`, never `domain`/`application`.
- More files and more indirection per feature than the flat layout — a real cost, accepted because this project's explicit purpose is demonstrating these patterns, not minimizing line count.
- `docs/adr/0008-openapi-resource-model.md`'s resources (Event, Endpoint, Delivery, Attempt) now each have a clear home: the domain entity of the same name in the corresponding module.
