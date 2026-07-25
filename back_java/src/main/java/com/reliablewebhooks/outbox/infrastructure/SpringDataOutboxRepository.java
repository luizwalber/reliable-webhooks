package com.reliablewebhooks.outbox.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {
}
