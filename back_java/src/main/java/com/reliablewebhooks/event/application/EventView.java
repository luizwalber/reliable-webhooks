package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.Event;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Use-case output — a read model, not the domain entity itself. */
public record EventView(
        UUID id,
        String eventType,
        String producerId,
        String idempotencyKey,
        String state,
        Map<String, Object> payload,
        OffsetDateTime receivedAt) {

    public static EventView from(Event event) {
        return new EventView(
                event.getId(),
                event.getEventType(),
                event.getProducerId(),
                event.getIdempotencyKey(),
                event.getState().name(),
                event.getPayload(),
                event.getReceivedAt());
    }
}
