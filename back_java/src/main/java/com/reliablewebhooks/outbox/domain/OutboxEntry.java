package com.reliablewebhooks.outbox.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One row per Event, written in the same transaction as the Event insert
 * (docs/adr/0003-transactional-outbox). No poller consumes this yet — that
 * is a later implementation slice. Pure domain — no persistence framework.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEntry {

    private final UUID id;
    private final UUID eventId;
    private final Map<String, Object> payload;
    private final boolean published;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime publishedAt;

    public static OutboxEntry forEvent(UUID eventId, Map<String, Object> payload) {
        return new OutboxEntry(UUID.randomUUID(), eventId, payload, false, OffsetDateTime.now(), null);
    }

    public static OutboxEntry reconstitute(UUID id, UUID eventId, Map<String, Object> payload, boolean published,
                                            OffsetDateTime createdAt, OffsetDateTime publishedAt) {
        return new OutboxEntry(id, eventId, payload, published, createdAt, publishedAt);
    }
}
