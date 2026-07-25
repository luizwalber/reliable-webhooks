package com.reliablewebhooks.event.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Pure domain entity — no persistence framework, no HTTP concerns. */
public class Event {

    private final UUID id;
    private final String eventType;
    private final String producerId;
    private final String idempotencyKey;
    private final Map<String, Object> payload;
    private final OffsetDateTime receivedAt;
    private EventState state;

    private Event(UUID id, String eventType, String producerId, String idempotencyKey,
                   Map<String, Object> payload, EventState state, OffsetDateTime receivedAt) {
        this.id = id;
        this.eventType = eventType;
        this.producerId = producerId;
        this.idempotencyKey = idempotencyKey;
        this.payload = payload;
        this.state = state;
        this.receivedAt = receivedAt;
    }

    /** Ingest a new Event, starting in state RECEIVED. */
    public static Event receive(String eventType, String producerId, String idempotencyKey, Map<String, Object> payload) {
        return new Event(UUID.randomUUID(), eventType, producerId, idempotencyKey, payload, EventState.RECEIVED, OffsetDateTime.now());
    }

    /** Rehydrate an Event from persistence — bypasses the receive() factory's initial-state rule. */
    public static Event reconstitute(UUID id, String eventType, String producerId, String idempotencyKey,
                                      Map<String, Object> payload, EventState state, OffsetDateTime receivedAt) {
        return new Event(id, eventType, producerId, idempotencyKey, payload, state, receivedAt);
    }

    /** The outbox row has been written in the same transaction (docs/adr/0003-transactional-outbox). */
    public void markOutboxed() {
        this.state = EventState.OUTBOXED;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getProducerId() {
        return producerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public EventState getState() {
        return state;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }
}
