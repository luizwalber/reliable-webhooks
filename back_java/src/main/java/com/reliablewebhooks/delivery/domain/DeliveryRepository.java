package com.reliablewebhooks.delivery.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Domain port. Implemented by delivery.infrastructure.DeliveryRepositoryAdapter. */
public interface DeliveryRepository {

    Page<Delivery> findByEventId(UUID eventId, Pageable pageable);
}
