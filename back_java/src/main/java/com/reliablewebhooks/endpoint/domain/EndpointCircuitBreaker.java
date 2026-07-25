package com.reliablewebhooks.endpoint.domain;

import java.util.UUID;

/**
 * Domain port over a per-Endpoint circuit breaker, get-or-create keyed by
 * Endpoint ID (docs/adr/0006-circuit-breaker). Implemented by
 * endpoint.infrastructure's Resilience4j adapter.
 */
public interface EndpointCircuitBreaker {

    /** False means the circuit is open — the caller should record CIRCUIT_OPEN and not make a real HTTP call. */
    boolean tryAcquirePermission(UUID endpointId);

    void recordSuccess(UUID endpointId);

    void recordFailure(UUID endpointId);

    /** Live registry state, for syncing Endpoint.circuitBreakerState's read-model snapshot. */
    CircuitBreakerState currentState(UUID endpointId);
}
