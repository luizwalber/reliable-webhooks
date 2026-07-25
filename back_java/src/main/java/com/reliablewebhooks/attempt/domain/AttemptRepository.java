package com.reliablewebhooks.attempt.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Domain port. Implemented by attempt.infrastructure.AttemptRepositoryAdapter. */
public interface AttemptRepository {

    Attempt save(Attempt attempt);

    /** Oldest first — the attempt timeline for a Delivery. */
    Page<Attempt> findByDeliveryId(UUID deliveryId, Pageable pageable);
}
