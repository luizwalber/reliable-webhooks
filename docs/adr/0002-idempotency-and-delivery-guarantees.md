# ADR-0002: Idempotency scope, delivery guarantees, and dedup mechanism

## Status

Accepted. Settled in [wayfinder ticket #5](https://github.com/luizwalber/reliable-webhooks/issues/5) (delivery-guarantee semantics) and [ticket #6](https://github.com/luizwalber/reliable-webhooks/issues/6) (idempotency key/TTL contract).

## Context

At-least-once delivery with dedup was fixed by the brief, but not *where* dedup is enforced, nor what happens at each hop: producer → ingest, ingest → Kafka, Kafka → consumer HTTP. Getting this wrong means either silent duplicate deliveries or a false sense of exactly-once.

## Decision

Four boundaries, one real enforcement point:

1. **Producer → Ingest**: client-supplied `Idempotency-Key` header, scoped per producer. Redis key `idempotency:ingest:{producerId}:{key}` → cached original response, TTL 24h. A duplicate key replays the exact original response — a replay, not a distinct "already received" signal.
2. **Ingest → Kafka**: duplicate Kafka messages are an accepted possibility (the outbox poller can crash between publish and marking the row published — see [ADR-0003](0003-transactional-outbox.md)). This boundary is deliberately *not* where dedup is enforced.
3. **Delivery-time dedup (the real mechanism)**: keyed by **(Event ID, Endpoint ID)** — fan-out means each endpoint has an independent delivery status for the same event ([ADR-0001](0001-fan-out-and-delivery-resource.md)). Postgres (the attempts table) is authoritative: "has any attempt for this (event, endpoint) already succeeded?" Redis is a fast-path positive-marker cache only (`delivery:done:{eventId}:{endpointId}` → true), TTL ~1h (covers the full retry ladder), set on success. Redis eviction/restart never causes an incorrect answer — worst case is one extra Postgres query, not a correctness gap. A duplicate caught here still produces an Attempt row with outcome `DEDUPED`, not a silent drop (see [ADR-0005](0005-state-machine.md)).
4. **Kafka → Consumer HTTP (last mile)**: even with delivery-time dedup, a real duplicate HTTP call is still theoretically possible (success, then a crash before marking DELIVERED, then a retry). Platform contract to consumers: **at-least-once, duplicates possible** — not exactly-once. Every webhook payload carries a stable `X-Webhook-Delivery-Id` alongside the HMAC signature ([ADR-0007](0007-hmac-signing.md)) so a non-idempotent consumer can dedupe on their own.

## Consequences

- One dedup mechanism, not two — boundary 3 alone is what a consumer's "why didn't I get charged twice" question resolves to.
- The attempts table is confirmed as the durable backing store: one row per (event, endpoint, attempt).
- Consumers that aren't idempotent are explicitly told they must be, via documented delivery-ID guidance — this is not the platform's problem to solve for them.
