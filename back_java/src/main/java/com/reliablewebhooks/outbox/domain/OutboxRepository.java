package com.reliablewebhooks.outbox.domain;

import java.util.List;

/** Domain port. Implemented by outbox.infrastructure.OutboxRepositoryAdapter. */
public interface OutboxRepository {

    OutboxEntry save(OutboxEntry entry);

    List<OutboxEntry> findAll();

    /**
     * Up to {@code batchSize} unpublished rows, locked against concurrent
     * pollers via {@code SELECT ... FOR UPDATE SKIP LOCKED}
     * (docs/adr/0003-transactional-outbox). Must be called within an
     * active transaction — the lock is held for that transaction's
     * duration.
     */
    List<OutboxEntry> findUnpublishedBatch(int batchSize);
}
