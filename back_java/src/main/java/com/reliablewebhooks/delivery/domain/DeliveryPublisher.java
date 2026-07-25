package com.reliablewebhooks.delivery.domain;

import java.time.OffsetDateTime;

/**
 * Domain port for putting a Delivery-attempt message onto the right Kafka
 * topic — main, a retry band, or the DLQ (docs/adr/0004-retry-policy-and-topic-topology).
 * Implemented by delivery.infrastructure.KafkaDeliveryPublisher.
 *
 * Every method defers its actual send until the caller's transaction
 * commits, if one is active — the send doesn't participate in that
 * transaction, so a fast consumer could otherwise see it before the row
 * it depends on is even visible. Callers can rely on this without
 * re-implementing it themselves.
 */
public interface DeliveryPublisher {

    /** First attempt, always the main topic — used by the outbox poller's fan-out. */
    void publish(Delivery delivery, int attemptNumber);

    /** True if a retry band exists for this attempt number; false means the budget is exhausted and the caller should route to the DLQ instead. */
    boolean hasRetryBandFor(int attemptNumber);

    /** Republish to the retry-band topic for this attempt number, with jitter applied. Returns the computed nextAttemptAt so the caller can persist it on the Delivery. */
    OffsetDateTime scheduleRetry(Delivery delivery, int attemptNumber);

    /** Attempts exhausted — publish to the DLQ topic (docs/adr/0005-state-machine: DLQ is a query filter, not a distinct message shape). */
    void publishToDeadLetter(Delivery delivery, int attemptNumber);
}
