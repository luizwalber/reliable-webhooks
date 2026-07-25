package com.reliablewebhooks.attempt.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAttemptRepository extends JpaRepository<AttemptJpaEntity, UUID> {

    Page<AttemptJpaEntity> findByDeliveryIdOrderByStartedAtAsc(UUID deliveryId, Pageable pageable);
}
