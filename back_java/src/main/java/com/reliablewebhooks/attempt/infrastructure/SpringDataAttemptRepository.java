package com.reliablewebhooks.attempt.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAttemptRepository extends JpaRepository<AttemptJpaEntity, UUID> {
}
