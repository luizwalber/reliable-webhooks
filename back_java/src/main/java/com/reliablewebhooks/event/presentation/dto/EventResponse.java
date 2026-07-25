package com.reliablewebhooks.event.presentation.dto;

import com.reliablewebhooks.event.application.EventView;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventType,
        String producerId,
        String idempotencyKey,
        String state,
        Map<String, Object> payload,
        OffsetDateTime receivedAt) {

    public static EventResponse from(EventView view) {
        return new EventResponse(
                view.id(),
                view.eventType(),
                view.producerId(),
                view.idempotencyKey(),
                view.state(),
                view.payload(),
                view.receivedAt());
    }
}
