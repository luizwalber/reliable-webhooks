package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.DeliveryState;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDeliveryRepository extends JpaRepository<DeliveryJpaEntity, UUID> {

    Page<DeliveryJpaEntity> findByEventId(UUID eventId, Pageable pageable);

    Page<DeliveryJpaEntity> findByStateAndEndpointId(DeliveryState state, UUID endpointId, Pageable pageable);

    Page<DeliveryJpaEntity> findByState(DeliveryState state, Pageable pageable);

    Page<DeliveryJpaEntity> findByEndpointId(UUID endpointId, Pageable pageable);
}
