package com.reliablewebhooks.endpoint.domain;

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
}
