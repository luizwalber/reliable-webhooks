package com.reliablewebhooks.attempt.presentation.dto;

import com.reliablewebhooks.attempt.application.AttemptView;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttemptResponse(
        UUID id,
        UUID deliveryId,
        int attemptNumber,
        String outcome,
        Integer httpStatusCode,
        String topic,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {

    public static AttemptResponse from(AttemptView view) {
        return new AttemptResponse(
                view.id(),
                view.deliveryId(),
                view.attemptNumber(),
                view.outcome(),
                view.httpStatusCode(),
                view.topic(),
                view.startedAt(),
                view.finishedAt());
    }
}
