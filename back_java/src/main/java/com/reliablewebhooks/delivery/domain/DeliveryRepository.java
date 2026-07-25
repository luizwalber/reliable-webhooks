package com.reliablewebhooks.delivery.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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

    /**
     * Apply a transition to an already-loaded Delivery and persist the
     * result in one call — a transition can't be applied without being
     * saved. For transitions on an existing Delivery only; a brand-new
     * Delivery's creation (Delivery.schedule(...)) still goes through
     * save() directly.
     */
    default Delivery apply(Delivery delivery, Consumer<Delivery> transition) {
        transition.accept(delivery);
        return save(delivery);
    }
}
