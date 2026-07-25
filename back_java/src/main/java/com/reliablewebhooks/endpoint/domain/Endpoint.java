package com.reliablewebhooks.endpoint.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Pure domain entity — no persistence framework, no HTTP concerns. */
public class Endpoint {

    private final UUID id;
    private final String url;
    private final String description;
    private final String secret;
    private final CircuitBreakerState circuitBreakerState;
    private final int successCount;
    private final int deadCount;
    private final OffsetDateTime createdAt;

    private Endpoint(UUID id, String url, String description, String secret, CircuitBreakerState circuitBreakerState,
                      int successCount, int deadCount, OffsetDateTime createdAt) {
        this.id = id;
        this.url = url;
        this.description = description;
        this.secret = secret;
        this.circuitBreakerState = circuitBreakerState;
        this.successCount = successCount;
        this.deadCount = deadCount;
        this.createdAt = createdAt;
    }

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

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getSecret() {
        return secret;
    }

    public CircuitBreakerState getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getDeadCount() {
        return deadCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
