package com.reliablewebhooks.endpoint.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEndpointRepository extends JpaRepository<EndpointJpaEntity, UUID> {

    Page<EndpointJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
