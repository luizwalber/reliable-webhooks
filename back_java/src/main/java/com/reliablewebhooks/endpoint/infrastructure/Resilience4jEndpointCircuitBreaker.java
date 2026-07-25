package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.CircuitBreakerState;
import com.reliablewebhooks.endpoint.domain.EndpointCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Get-or-create a Resilience4j CircuitBreaker per Endpoint ID from a shared
 * registry — avoids needing every endpoint's name at config-file time,
 * since endpoints are created dynamically via the API (docs/adr/0006).
 */
@Component
@RequiredArgsConstructor
class Resilience4jEndpointCircuitBreaker implements EndpointCircuitBreaker {

    private final CircuitBreakerRegistry registry;

    @Override
    public boolean tryAcquirePermission(UUID endpointId) {
        return breakerFor(endpointId).tryAcquirePermission();
    }

    @Override
    public void recordSuccess(UUID endpointId) {
        breakerFor(endpointId).onSuccess(0, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFailure(UUID endpointId) {
        breakerFor(endpointId).onError(0, TimeUnit.NANOSECONDS, new DeliveryAttemptFailedException());
    }

    @Override
    public CircuitBreakerState currentState(UUID endpointId) {
        return switch (breakerFor(endpointId).getState()) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            default -> throw new IllegalStateException("Unexpected circuit-breaker state: " + breakerFor(endpointId).getState());
        };
    }

    private CircuitBreaker breakerFor(UUID endpointId) {
        return registry.circuitBreaker(endpointId.toString());
    }

    /** A synthetic cause for onError — resilience4j only uses it for its recorded-exception event log; no exception ever actually propagates from a real delivery attempt. */
    private static final class DeliveryAttemptFailedException extends RuntimeException {
        DeliveryAttemptFailedException() {
            super("Delivery attempt did not succeed", null, false, false);
        }
    }
}
