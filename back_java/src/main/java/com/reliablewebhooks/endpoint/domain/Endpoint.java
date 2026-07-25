package com.reliablewebhooks.endpoint.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Pure domain entity — no persistence framework, no HTTP concerns. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Endpoint {

    private final UUID id;
    private final String url;
    private final String description;
    private final String secret;
    private CircuitBreakerState circuitBreakerState;
    private int successCount;
    private int deadCount;
    private final OffsetDateTime createdAt;

    /** Register a new Endpoint. The secret is generated once, by a SecretGenerator port, and never rotated. */
    public static Endpoint register(String url, String description, String secret) {
        return new Endpoint(UUID.randomUUID(), url, description, secret, CircuitBreakerState.CLOSED, 0, 0, OffsetDateTime.now());
    }

    public static Endpoint reconstitute(UUID id, String url, String description, String secret,
                                         CircuitBreakerState circuitBreakerState, int successCount, int deadCount,
                                         OffsetDateTime createdAt) {
        return new Endpoint(id, url, description, secret, circuitBreakerState, successCount, deadCount, createdAt);
    }

    /** successCount / (successCount + deadCount); null (not a divide-by-zero) when both are zero. See docs/adr/0009-metrics. */
    public Double successRate() {
        int total = successCount + deadCount;
        if (total == 0) {
            return null;
        }
        return (double) successCount / total;
    }

    /** A Delivery to this Endpoint reached DELIVERED (docs/adr/0009-metrics). */
    public void recordSuccess() {
        this.successCount++;
    }

    /** A Delivery to this Endpoint reached DEAD (docs/adr/0009-metrics). */
    public void recordFailure() {
        this.deadCount++;
    }

    /** Sync the read-model snapshot with the live circuit-breaker registry state (docs/adr/0006-circuit-breaker). */
    public void updateCircuitBreakerState(CircuitBreakerState circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }
}
