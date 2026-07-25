package com.reliablewebhooks.delivery.application;

import com.reliablewebhooks.delivery.domain.Delivery;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DeliveryView(
        UUID id,
        UUID eventId,
        UUID endpointId,
        String state,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DeliveryView from(Delivery delivery) {
        return new DeliveryView(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEndpointId(),
                delivery.getState().name(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
