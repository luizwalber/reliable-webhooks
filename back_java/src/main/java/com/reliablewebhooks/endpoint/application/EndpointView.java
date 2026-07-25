package com.reliablewebhooks.endpoint.application;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Use-case output — a read model, not the domain entity itself. Always
 * carries the secret; presentation decides per-DTO whether to expose it
 * (EndpointCreatedResponse does, EndpointResponse never does).
 */
public record EndpointView(
        UUID id,
        String url,
        String description,
        String circuitBreakerState,
        int successCount,
        int deadCount,
        Double successRate,
        OffsetDateTime createdAt,
        String secret) {

    public static EndpointView from(Endpoint endpoint) {
        return new EndpointView(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.getDescription(),
                endpoint.getCircuitBreakerState().name(),
                endpoint.getSuccessCount(),
                endpoint.getDeadCount(),
                endpoint.successRate(),
                endpoint.getCreatedAt(),
                endpoint.getSecret());
    }
}
