package com.reliablewebhooks.outbox.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One row per Event, written in the same transaction as the Event insert
 * (docs/adr/0003-transactional-outbox). Pure domain — no persistence framework.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEntry {

    private final UUID id;
    private final UUID eventId;
    private final Map<String, Object> payload;
    private boolean published;
    private final OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;

    public static OutboxEntry forEvent(UUID eventId, Map<String, Object> payload) {
        return new OutboxEntry(UUID.randomUUID(), eventId, payload, false, OffsetDateTime.now(), null);
    }

    public static OutboxEntry reconstitute(UUID id, UUID eventId, Map<String, Object> payload, boolean published,
                                            OffsetDateTime createdAt, OffsetDateTime publishedAt) {
        return new OutboxEntry(id, eventId, payload, published, createdAt, publishedAt);
    }

    /** Fan-out is complete and every Kafka message has been sent (docs/adr/0003-transactional-outbox). */
    public void markPublished() {
        this.published = true;
        this.publishedAt = OffsetDateTime.now();
    }
}
