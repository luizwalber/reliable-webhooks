# ADR-0006: Per-endpoint circuit breaker configuration

## Status

Accepted. Grounded by [wayfinder research ticket #4](https://github.com/luizwalber/reliable-webhooks/issues/4), thresholds locked in [ticket #11](https://github.com/luizwalber/reliable-webhooks/issues/11).

## Context

The brief fixed Resilience4j and "per-endpoint circuit breaker" but not how a dynamic, per-instance breaker is actually registered in Spring Boot (Resilience4j's Spring Boot starter is built around static, config-file-declared breaker names), nor the concrete thresholds.

## Decision

- **Registration**: `CircuitBreakerRegistry.circuitBreaker(name, config)` used as a get-or-create cache, keyed by Endpoint ID — avoids needing to know every endpoint's name at config-file time, since endpoints are created dynamically via the API.
- **Sliding window**: `COUNT_BASED`, size 5, minimum calls 5 (suits bursty demo traffic better than a time-based window).
- **Failure-rate threshold**: 50% (Resilience4j's own default).
- **Recovery cycle**: 30s wait in `OPEN` before `HALF_OPEN`; 2 permitted trial calls in `HALF_OPEN` (both must succeed to close).
- **`CIRCUIT_OPEN` consumes a retry attempt** from the same 4-attempt budget as real failures ([ADR-0004](0004-retry-policy-and-topic-topology.md)) — no special-casing. This is a deliberate "fail-fast to DLQ" interaction: once an endpoint's circuit opens, its events reach the DLQ quickly (within the ~3-minute retry ladder) instead of blindly retrying against a known-down endpoint.
- **Global fixed defaults only** — no per-endpoint override fields on the Endpoint resource; every endpoint gets the same thresholds.

## Consequences

- One registry-backed cache instance in the delivery worker, not N static bean definitions.
- An endpoint with an open circuit visibly and quickly accumulates `DEAD` deliveries — good for the demo story ("watch a bad endpoint get isolated"), and consistent with `circuitBreakerState` already being on the Endpoint resource in `openapi.yaml`.
- No per-endpoint tuning UI/API needed — smaller surface area, deferred as a non-goal rather than an oversight.
