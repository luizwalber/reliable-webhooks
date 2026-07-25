# Reliable Webhooks — Context

Single-context repo: a webhook-delivery platform. A producer ingests an **Event**; the platform fans it out to every registered **Endpoint** as an independent, retried, circuit-broken, HMAC-signed HTTP delivery. Portfolio/demo scale, not production throughput — see `docs/adr/` for the reasoning behind every load-bearing decision below, and the closed tickets on the [wayfinder map](https://github.com/luizwalber/reliable-webhooks/issues/1) for the full deliberation.

## Vocabulary

- **Event** — a fact ingested from a producer via `POST /v1/events`, carrying a client-supplied `Idempotency-Key`. Read-only once created. States: `RECEIVED → OUTBOXED → PUBLISHED` (terminal). An Event is never itself "delivered" or "dead" — see Delivery. [ADR-0001](docs/adr/0001-fan-out-and-delivery-resource.md), [ADR-0005](docs/adr/0005-state-machine.md).
- **Endpoint** — a producer-registered HTTP destination (URL + HMAC secret, shown once). Every PUBLISHED Event fans out to *every* registered Endpoint (broadcast, not producer-targeted). Carries cumulative `successCount`/`deadCount`/`successRate` and `circuitBreakerState`. [ADR-0001](docs/adr/0001-fan-out-and-delivery-resource.md), [ADR-0009](docs/adr/0009-metrics.md).
- **Delivery** — the unit of delivery state: one per (Event, Endpoint) pair. Carries the states real webhook platforms care about: `SCHEDULED → DELIVERING → DELIVERED` (terminal) / `DELIVERING → DEAD` (terminal, attempts exhausted) / `DEAD → SCHEDULED` (manual retry). [ADR-0001](docs/adr/0001-fan-out-and-delivery-resource.md), [ADR-0005](docs/adr/0005-state-machine.md).
- **Attempt** — one HTTP try (or short-circuit) belonging to a Delivery. `outcome` is null while in flight; once resolved, one of `SUCCESS, TIMEOUT, HTTP_5XX, HTTP_4XX, CIRCUIT_OPEN, DEDUPED`. [ADR-0005](docs/adr/0005-state-machine.md).
- **Outbox** — the transactional-outbox table backing reliable Event → Kafka publication; drained by a `@Scheduled` poller, not CDC. [ADR-0003](docs/adr/0003-transactional-outbox.md).
- **Idempotency Key** — client-supplied header at ingest, scoped per producer, 24h TTL, replays the original response on collision. Distinct from delivery-time dedup, which is keyed by (Event, Endpoint) and is the platform's real duplicate-prevention mechanism. [ADR-0002](docs/adr/0002-idempotency-and-delivery-guarantees.md).
- **DLQ (dead-letter queue)** — not a separate resource; it's the set of Deliveries in state `DEAD` (`GET /v1/deliveries?state=DEAD`). Manual retry (`POST /v1/deliveries/{id}/retry`) re-enters the normal retry machinery — no special code path. [ADR-0004](docs/adr/0004-retry-policy-and-topic-topology.md), [ADR-0005](docs/adr/0005-state-machine.md).
- **Circuit breaker** — per-Endpoint Resilience4j breaker, get-or-create from a registry keyed by Endpoint ID. `CIRCUIT_OPEN` is a real Attempt outcome, not a bypass. [ADR-0006](docs/adr/0006-circuit-breaker.md).
- **Simulator** — a standalone out-of-band service (not part of the public API) exposing failure-mode routes (`/simulate/success`, `/simulate/error-500`, `/simulate/timeout`, `/simulate/intermittent`) registered as ordinary Endpoints, for demoing retry/DLQ/circuit-breaker behavior. [ADR-0010](docs/adr/0010-simulated-consumers.md).

## ADR index

See `docs/adr/` for the full set, one per load-bearing decision:

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-fan-out-and-delivery-resource.md) | Fan-out to all Endpoints; Delivery as a distinct resource from Event |
| [0002](docs/adr/0002-idempotency-and-delivery-guarantees.md) | Idempotency scope, delivery guarantees, and dedup mechanism |
| [0003](docs/adr/0003-transactional-outbox.md) | Transactional outbox via scheduled poller, not CDC |
| [0004](docs/adr/0004-retry-policy-and-topic-topology.md) | Retry policy and Kafka topic topology |
| [0005](docs/adr/0005-state-machine.md) | Event/Delivery/Attempt state machine and DLQ reprocessing |
| [0006](docs/adr/0006-circuit-breaker.md) | Per-endpoint circuit breaker configuration |
| [0007](docs/adr/0007-hmac-signing.md) | HMAC signing scheme |
| [0008](docs/adr/0008-openapi-resource-model.md) | OpenAPI resource model and API surface |
| [0009](docs/adr/0009-metrics.md) | Metrics and success-rate definition |
| [0010](docs/adr/0010-simulated-consumers.md) | Simulated-consumer shape |
| [0011](docs/adr/0011-docker-compose-topology.md) | Docker Compose service topology |
| [0012](docs/adr/0012-contract-test-tooling.md) | Contract-test tooling |
| [0013](docs/adr/0013-clean-architecture-layering.md) | Clean Architecture layering for the backend |
| [0014](docs/adr/0014-docker-compose-test-seam.md) | Docker Compose-based local test seam (replaces Testcontainers) |

## Out of scope

NestJS backend, Flutter frontend, multi-region/multi-broker Kafka HA, API-level authn/authz (only HMAC on outbound deliveries is in scope), gateway/load-balancer/observability (deferred until core is stable), HMAC secret rotation.
