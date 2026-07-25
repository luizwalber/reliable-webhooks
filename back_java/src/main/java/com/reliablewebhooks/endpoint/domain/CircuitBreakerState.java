package com.reliablewebhooks.endpoint.domain;

/** See docs/adr/0006-circuit-breaker. No breaker logic runs in this implementation slice. */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
