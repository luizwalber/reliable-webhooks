package com.reliablewebhooks.event.application;

import java.util.Optional;

/**
 * Port for the ingest-time idempotency replay check (docs/adr/0002-idempotency-and-delivery-guarantees,
 * boundary 1). Lives here — not in a shared/domain location — because Event
 * ingestion is its only consumer; the `idempotency` package has no
 * application layer of its own (see .claude/clean-architecture.mdc) and
 * implements this port directly from its infrastructure layer.
 */
public interface IdempotencyGateway {

    Optional<CachedReplay> find(String producerId, String idempotencyKey);

    void store(String producerId, String idempotencyKey, CachedReplay replay);

    /** The exact response a replayed request should reproduce, byte-for-byte. */
    record CachedReplay(int status, String body) {
    }
}
