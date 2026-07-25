package com.reliablewebhooks.event.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEventRepository extends JpaRepository<EventJpaEntity, UUID> {

    Page<EventJpaEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
