package com.reliablewebhooks.delivery.presentation.dto;

import com.reliablewebhooks.delivery.application.DeliveryView;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID eventId,
        UUID endpointId,
        String state,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DeliveryResponse from(DeliveryView view) {
        return new DeliveryResponse(
                view.id(),
                view.eventId(),
                view.endpointId(),
                view.state(),
                view.attemptCount(),
                view.nextAttemptAt(),
                view.createdAt(),
                view.updatedAt());
    }
}
