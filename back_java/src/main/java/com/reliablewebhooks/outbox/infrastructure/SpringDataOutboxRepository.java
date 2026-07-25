package com.reliablewebhooks.outbox.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    /**
     * Native query because Spring Data's @Lock abstraction has no SKIP
     * LOCKED mode — plain SQL is the clearest way to express exactly the
     * locking behavior docs/adr/0003-transactional-outbox calls for.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published = false
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxJpaEntity> findUnpublishedBatchForUpdateSkipLocked(@Param("batchSize") int batchSize);
}
