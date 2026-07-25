package com.reliablewebhooks.delivery.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One row per (Event, Endpoint) pair (docs/adr/0001-fan-out-and-delivery-resource).
 * Pure domain — no persistence framework. Nothing constructs a Delivery in
 * the current implementation slice; reconstitute() exists for the day the
 * outbox poller + delivery workers slice starts writing rows.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery {

    private final UUID id;
    private final UUID eventId;
    private final UUID endpointId;
    private final DeliveryState state;
    private final int attemptCount;
    private final OffsetDateTime nextAttemptAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static Delivery reconstitute(UUID id, UUID eventId, UUID endpointId, DeliveryState state, int attemptCount,
                                         OffsetDateTime nextAttemptAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Delivery(id, eventId, endpointId, state, attemptCount, nextAttemptAt, createdAt, updatedAt);
    }
}
