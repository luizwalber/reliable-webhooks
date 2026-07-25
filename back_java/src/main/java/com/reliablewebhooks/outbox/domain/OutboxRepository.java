package com.reliablewebhooks.outbox.domain;

import java.util.List;

/** Domain port. Implemented by outbox.infrastructure.OutboxRepositoryAdapter. */
public interface OutboxRepository {

    OutboxEntry save(OutboxEntry entry);

    List<OutboxEntry> findAll();
}
