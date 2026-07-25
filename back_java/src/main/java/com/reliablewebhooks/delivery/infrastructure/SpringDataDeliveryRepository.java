package com.reliablewebhooks.delivery.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDeliveryRepository extends JpaRepository<DeliveryJpaEntity, UUID> {

    Page<DeliveryJpaEntity> findByEventId(UUID eventId, Pageable pageable);
}
