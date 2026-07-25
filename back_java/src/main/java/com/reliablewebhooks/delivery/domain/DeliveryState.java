package com.reliablewebhooks.delivery.domain;

/**
 * SCHEDULED is both the initial state (right after fan-out creates the
 * Delivery) and the post-failure/awaiting-next-band state. See
 * docs/adr/0005-state-machine. No code creates Delivery rows in the
 * current implementation slice — fan-out is a later slice (outbox poller
 * + delivery workers).
 */
public enum DeliveryState {
    SCHEDULED,
    DELIVERING,
    DELIVERED,
    DEAD
}
