package com.reliablewebhooks.delivery.domain;

import com.reliablewebhooks.attempt.domain.AttemptOutcome;

/** The outcome of one real EndpointDeliveryClient HTTP call. httpStatusCode is null on a transport-level failure (TIMEOUT). */
public record DeliveryAttemptResult(AttemptOutcome outcome, Integer httpStatusCode) {
}
