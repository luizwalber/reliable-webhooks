package com.reliablewebhooks.delivery.domain;

import java.util.UUID;

/**
 * Delivery-time dedup fast path, keyed by (Event ID, Endpoint ID)
 * (docs/adr/0002-idempotency-and-delivery-guarantees, boundary 3). Postgres
 * (the already-loaded Delivery's state) is the authoritative fallback —
 * this port is only the Redis-backed positive-marker cache, never the sole
 * source of truth.
 */
public interface DeliveryDedupChecker {

    boolean isMarkedDelivered(UUID eventId, UUID endpointId);

    void markDelivered(UUID eventId, UUID endpointId);
}
