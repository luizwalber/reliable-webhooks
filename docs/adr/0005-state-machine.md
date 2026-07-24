# ADR-0005: Event/Delivery/Attempt state machine and DLQ reprocessing

## Status

Accepted. Settled in [wayfinder ticket #10](https://github.com/luizwalber/reliable-webhooks/issues/10) (state machine) and [ticket #12](https://github.com/luizwalber/reliable-webhooks/issues/12) (DLQ reprocessing semantics).

## Context

With Event and Delivery established as separate resources ([ADR-0001](0001-fan-out-and-delivery-resource.md)), each needed its own canonical state machine, plus a decision on what an individual Attempt outcome looks like and how "retry a dead delivery" actually re-enters the system.

## Decision

**Event**: `RECEIVED → OUTBOXED → PUBLISHED` (terminal). `PUBLISHED` has no failure state — the outbox poller's re-poll handles recovery, not a modeled Event state.

**Delivery**:
```
SCHEDULED → DELIVERING → DELIVERED       (terminal, success)
DELIVERING → SCHEDULED                    (failure, retries remain)
DELIVERING → DEAD                         (terminal, attempts exhausted)
DEAD → SCHEDULED                          (manual retry)
```
`SCHEDULED` is one name for both the initial state (right after fan-out) and the post-failure/awaiting-next-band state — it was originally named `AWAITING_RETRY` and renamed for that reason.

**Attempt**: created when the worker begins processing (after the dedup check passes). `outcome` and `finishedAt` are both null while in flight — `outcome: null` is itself the in-flight signal, no separate status field. Once resolved: `SUCCESS, TIMEOUT, HTTP_5XX, HTTP_4XX, CIRCUIT_OPEN, DEDUPED`. `DEDUPED` covers a duplicate Kafka message caught by delivery-time dedup ([ADR-0002](0002-idempotency-and-delivery-guarantees.md)) against an already-`DELIVERED` Delivery — it still creates an Attempt row (no real HTTP call), keeping the dedup event visible in the timeline instead of silently dropping it, the same precedent as `CIRCUIT_OPEN` ([ADR-0006](0006-circuit-breaker.md)).

**DLQ reprocessing** (manual retry, `POST /v1/deliveries/{id}/retry`): no special code path. It is a two-field mutation — `attemptCount=0`, `state=SCHEDULED`, `nextAttemptAt=now` — publishing a fresh Delivery-attempt message to `webhook.delivery.main`, the exact same path a brand-new Delivery takes. No direct-HTTP-from-API-thread shortcut, and no circuit-state check at request time: the endpoint accepts the retry unconditionally, and if the circuit is `OPEN`, the retry simply short-circuits to `CIRCUIT_OPEN` on its next attempt like any other attempt would.

## Consequences

- One combined mental model (event + attempts-log) rather than two independently-tracked machines per resource, matching what the prototype validated.
- "DLQ" is never a distinct resource or code path anywhere in the system — it's a query filter (`state=DEAD`) plus the normal retry machinery. Simplifies the implementation considerably: there is no DLQ-specific redelivery logic to write or test.
- A retried Delivery against a still-open circuit will visibly fail fast (`CIRCUIT_OPEN`) rather than silently succeed or silently get rejected — consistent with the retry ladder's normal behavior.
