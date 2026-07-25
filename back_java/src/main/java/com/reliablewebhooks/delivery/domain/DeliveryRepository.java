package com.reliablewebhooks.delivery.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Domain port. Implemented by delivery.infrastructure.DeliveryRepositoryAdapter. */
public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    Optional<Delivery> findById(UUID id);

    boolean existsById(UUID id);

    Page<Delivery> findByEventId(UUID eventId, Pageable pageable);

    /** Either filter may be null, meaning "no filter on that field" — GET /deliveries, including the DLQ view (state=DEAD). */
    Page<Delivery> search(DeliveryState state, UUID endpointId, Pageable pageable);
}
