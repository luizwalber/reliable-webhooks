package com.reliablewebhooks.attempt.domain;

/**
 * DEDUPED covers a duplicate Kafka message caught by delivery-time dedup
 * against an already-DELIVERED Delivery — no real HTTP call is made, same
 * as CIRCUIT_OPEN, so the dedup mechanism stays visible in the attempts
 * timeline instead of silently dropping the message. See
 * docs/adr/0005-state-machine.
 */
public enum AttemptOutcome {
    SUCCESS,
    TIMEOUT,
    HTTP_5XX,
    HTTP_4XX,
    CIRCUIT_OPEN,
    DEDUPED
}
