package com.reliablewebhooks.event.domain;

/**
 * Covers only the ingest-to-fan-out lifecycle; PUBLISHED is terminal (no
 * failure state — the outbox poller's own re-poll mechanism handles
 * recovery, it isn't modeled as an Event state). Per-endpoint delivery
 * outcome lives on Delivery, not here. See docs/adr/0005-state-machine.
 */
public enum EventState {
    RECEIVED,
    OUTBOXED,
    PUBLISHED
}
