package com.reliablewebhooks.event.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Pure domain entity — no persistence framework, no HTTP concerns. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Event {

    private final UUID id;
    private final String eventType;
    private final String producerId;
    private final String idempotencyKey;
    private final Map<String, Object> payload;
    private final OffsetDateTime receivedAt;
    private EventState state;

    /** Ingest a new Event, starting in state RECEIVED. */
    public static Event receive(String eventType, String producerId, String idempotencyKey, Map<String, Object> payload) {
        return new Event(UUID.randomUUID(), eventType, producerId, idempotencyKey, payload, OffsetDateTime.now(), EventState.RECEIVED);
    }

    /** Rehydrate an Event from persistence — bypasses the receive() factory's initial-state rule. */
    public static Event reconstitute(UUID id, String eventType, String producerId, String idempotencyKey,
                                      Map<String, Object> payload, EventState state, OffsetDateTime receivedAt) {
        return new Event(id, eventType, producerId, idempotencyKey, payload, receivedAt, state);
    }

    /** The outbox row has been written in the same transaction (docs/adr/0003-transactional-outbox). */
    public void markOutboxed() {
        this.state = EventState.OUTBOXED;
    }

    /** Fan-out is complete and every Delivery's Kafka message has been sent (docs/adr/0003-transactional-outbox). Terminal — see docs/adr/0005-state-machine. */
    public void markPublished() {
        this.state = EventState.PUBLISHED;
    }
}
