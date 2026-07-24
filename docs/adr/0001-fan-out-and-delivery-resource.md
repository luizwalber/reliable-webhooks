# ADR-0001: Fan-out to all Endpoints; Delivery as a resource distinct from Event

## Status

Accepted. Settled in [wayfinder ticket #6](https://github.com/luizwalber/reliable-webhooks/issues/6), reflected in [ticket #8](https://github.com/luizwalber/reliable-webhooks/issues/8)'s resource model.

## Context

The project brief specified events, endpoints, and retries but left one thing genuinely unstated: when a producer POSTs an Event, does it go to one specific endpoint (targeted), or every endpoint the producer has registered (broadcast)? This shapes the whole resource model — if delivery state is per-endpoint, an Event can't itself carry a single "delivered/dead" status once more than one endpoint exists.

## Decision

An Event fans out to **all** registered Endpoints — broadcast, not producer-targeted. Delivery state is tracked per **(Event, Endpoint)** pair via a dedicated **Delivery** resource. An Event is never "delivered" or "dead" itself; it only ever reaches `PUBLISHED` (terminal) once handed to Kafka. Each Delivery independently reaches `DELIVERED` or `DEAD`.

## Consequences

- The API surface needs four resources, not three: Event, Endpoint, Delivery, Attempt (see [ADR-0008](0008-openapi-resource-model.md)).
- Delivery-time dedup, retry counters, and circuit-breaker consultation are all scoped per (Event, Endpoint) — see [ADR-0002](0002-idempotency-and-delivery-guarantees.md) and [ADR-0006](0006-circuit-breaker.md).
- Registering N endpoints multiplies delivery volume by N for every Event — acceptable at demo scale, called out explicitly as a non-goal to optimize.
