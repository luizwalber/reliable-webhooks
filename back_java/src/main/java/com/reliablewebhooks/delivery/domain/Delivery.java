package com.reliablewebhooks.delivery.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per (Event, Endpoint) pair (docs/adr/0001-fan-out-and-delivery-resource).
 * Pure domain — no persistence framework. Nothing constructs a Delivery in
 * the current implementation slice; reconstitute() exists for the day the
 * outbox poller + delivery workers slice starts writing rows.
 */
public class Delivery {

    private final UUID id;
    private final UUID eventId;
    private final UUID endpointId;
    private final DeliveryState state;
    private final int attemptCount;
    private final OffsetDateTime nextAttemptAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private Delivery(UUID id, UUID eventId, UUID endpointId, DeliveryState state, int attemptCount,
                      OffsetDateTime nextAttemptAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.state = state;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Delivery reconstitute(UUID id, UUID eventId, UUID endpointId, DeliveryState state, int attemptCount,
                                         OffsetDateTime nextAttemptAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Delivery(id, eventId, endpointId, state, attemptCount, nextAttemptAt, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public DeliveryState getState() {
        return state;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
