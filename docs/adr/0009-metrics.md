# ADR-0009: Metrics and success-rate definition

## Status

Accepted. Settled in [wayfinder ticket #14](https://github.com/luizwalber/reliable-webhooks/issues/14).

## Context

"Success-rate visibility per endpoint" was implied by the demo's purpose (showing retry/circuit-breaker behavior) but the brief didn't define what counts as success/failure, the aggregation window, or whether it needs a dedicated endpoint.

## Decision

- **Success** = a Delivery reaching `DELIVERED`. **Failure** = a Delivery reaching `DEAD`. In-progress deliveries (`SCHEDULED`/`DELIVERING`) are excluded from the denominator entirely.
- `successRate = DELIVERED / (DELIVERED + DEAD)`, `null` (not a divide-by-zero) when both counts are zero.
- **Per-endpoint is the primary metric**; platform-wide is a free client-side roll-up over the same per-endpoint data — no separate platform-wide aggregation to compute or cache server-side.
- **All-time cumulative only** — no rolling window, no time-bucketed metrics.
- **Embedded directly on the Endpoint resource** (`successCount`, `deadCount`, `successRate`) — no dedicated `/v1/metrics/...` endpoint.

## Consequences

- Counts increment in the same transaction that moves a Delivery to a terminal state — no separate metrics-aggregation job.
- No time-series storage or windowing logic to build; the whole feature is three extra columns on Endpoint plus an increment on terminal transition.
- If a future "recent success rate" view is ever wanted, it requires a new decision (this ADR only covers all-time cumulative) — not a silent extension of these fields.
