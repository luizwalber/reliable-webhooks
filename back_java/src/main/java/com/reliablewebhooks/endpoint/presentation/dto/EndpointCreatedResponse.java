package com.reliablewebhooks.endpoint.presentation.dto;

import com.reliablewebhooks.endpoint.application.EndpointView;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Returned only from POST /endpoints — the one and only time the secret is visible. */
public record EndpointCreatedResponse(
        UUID id,
        String url,
        String description,
        String circuitBreakerState,
        int successCount,
        int deadCount,
        Double successRate,
        OffsetDateTime createdAt,
        String secret) {

    public static EndpointCreatedResponse from(EndpointView view) {
        return new EndpointCreatedResponse(
                view.id(),
                view.url(),
                view.description(),
                view.circuitBreakerState(),
                view.successCount(),
                view.deadCount(),
                view.successRate(),
                view.createdAt(),
                view.secret());
    }
}
