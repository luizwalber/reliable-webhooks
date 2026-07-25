package com.reliablewebhooks.endpoint.presentation.dto;

import com.reliablewebhooks.endpoint.application.EndpointView;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Never carries the secret — it is only ever returned once, at creation (see EndpointCreatedResponse). */
public record EndpointResponse(
        UUID id,
        String url,
        String description,
        String circuitBreakerState,
        int successCount,
        int deadCount,
        Double successRate,
        OffsetDateTime createdAt) {

    public static EndpointResponse from(EndpointView view) {
        return new EndpointResponse(
                view.id(),
                view.url(),
                view.description(),
                view.circuitBreakerState(),
                view.successCount(),
                view.deadCount(),
                view.successRate(),
                view.createdAt());
    }
}
