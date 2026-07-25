package com.reliablewebhooks.endpoint.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Domain port. Implemented by endpoint.infrastructure.EndpointRepositoryAdapter. */
public interface EndpointRepository {

    Endpoint save(Endpoint endpoint);

    Optional<Endpoint> findById(UUID id);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable);

    /** Every registered Endpoint, unpaged — used by outbox fan-out (docs/adr/0001-fan-out-and-delivery-resource). */
    List<Endpoint> findAll();
}
