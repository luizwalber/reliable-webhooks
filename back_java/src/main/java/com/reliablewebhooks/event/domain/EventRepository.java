package com.reliablewebhooks.event.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain port. Implemented by event.infrastructure.EventRepositoryAdapter.
 * Uses Spring Data's Pageable/Page — a generic pagination contract, not an
 * ORM or HTTP concern — as a pragmatic exception to "no framework in domain".
 */
public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    boolean existsById(UUID id);

    long count();

    List<Event> findAll();

    Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable);
}
