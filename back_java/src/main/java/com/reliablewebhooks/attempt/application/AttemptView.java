package com.reliablewebhooks.attempt.application;

import com.reliablewebhooks.attempt.domain.Attempt;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttemptView(
        UUID id,
        UUID deliveryId,
        int attemptNumber,
        String outcome,
        Integer httpStatusCode,
        String topic,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {

    public static AttemptView from(Attempt attempt) {
        return new AttemptView(
                attempt.getId(),
                attempt.getDeliveryId(),
                attempt.getAttemptNumber(),
                attempt.getOutcome() == null ? null : attempt.getOutcome().name(),
                attempt.getHttpStatusCode(),
                attempt.getTopic(),
                attempt.getStartedAt(),
                attempt.getFinishedAt());
    }
}
