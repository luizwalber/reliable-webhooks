package com.reliablewebhooks.delivery.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One row per (Event, Endpoint) pair (docs/adr/0001-fan-out-and-delivery-resource).
 * Pure domain — no persistence framework.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery {

    private final UUID id;
    private final UUID eventId;
    private final UUID endpointId;
    private DeliveryState state;
    private int attemptCount;
    private OffsetDateTime nextAttemptAt;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Fan-out creates a Delivery in state SCHEDULED, zero attempts, no next-attempt time yet (docs/adr/0005-state-machine). */
    public static Delivery schedule(UUID eventId, UUID endpointId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Delivery(UUID.randomUUID(), eventId, endpointId, DeliveryState.SCHEDULED, 0, null, now, now);
    }

    public static Delivery reconstitute(UUID id, UUID eventId, UUID endpointId, DeliveryState state, int attemptCount,
                                         OffsetDateTime nextAttemptAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Delivery(id, eventId, endpointId, state, attemptCount, nextAttemptAt, createdAt, updatedAt);
    }

    /** The worker has begun processing this Delivery's next attempt (docs/adr/0005-state-machine). */
    public void startDelivering() {
        this.state = DeliveryState.DELIVERING;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Terminal: the attempt succeeded (docs/adr/0005-state-machine). */
    public void markDelivered() {
        this.state = DeliveryState.DELIVERED;
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    /** A retryable failure with attempts remaining — re-enters the retry ladder at the given time (docs/adr/0004-retry-policy-and-topic-topology). */
    public void scheduleRetry(OffsetDateTime nextAttemptAt) {
        this.state = DeliveryState.SCHEDULED;
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Terminal: attempts exhausted (docs/adr/0004-retry-policy-and-topic-topology, docs/adr/0005-state-machine). CIRCUIT_OPEN also consumes a budget slot (docs/adr/0006-circuit-breaker). */
    public void markDead() {
        this.state = DeliveryState.DEAD;
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.updatedAt = OffsetDateTime.now();
    }
}
