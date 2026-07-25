package com.reliablewebhooks.attempt.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One HTTP try (or short-circuit) belonging to a Delivery. Pure domain — no
 * persistence framework. See docs/adr/0005-state-machine.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Attempt {

    private final UUID id;
    private final UUID deliveryId;
    private final int attemptNumber;
    private AttemptOutcome outcome;
    private Integer httpStatusCode;
    private final String topic;
    private final OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    /** Created the moment the worker begins processing — outcome/finishedAt null is itself the in-flight signal. */
    public static Attempt start(UUID deliveryId, int attemptNumber, String topic) {
        return new Attempt(UUID.randomUUID(), deliveryId, attemptNumber, null, null, topic, OffsetDateTime.now(), null);
    }

    public static Attempt reconstitute(UUID id, UUID deliveryId, int attemptNumber, AttemptOutcome outcome,
            Integer httpStatusCode, String topic, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        return new Attempt(id, deliveryId, attemptNumber, outcome, httpStatusCode, topic, startedAt, finishedAt);
    }

    /** Resolve an in-flight Attempt once its outcome (real HTTP call, dedup, or circuit-open short-circuit) is known. */
    public void resolve(AttemptOutcome outcome, Integer httpStatusCode) {
        this.outcome = outcome;
        this.httpStatusCode = httpStatusCode;
        this.finishedAt = OffsetDateTime.now();
    }
}
