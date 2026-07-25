package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.CircuitBreakerState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Setters exist for MapStruct's no-arg-constructor-plus-setters mapping strategy — see .claude/mapstruct.mdc. */
@Entity
@Table(name = "endpoints")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EndpointJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String url;

    private String description;

    @Column(nullable = false)
    private String secret;

    @Enumerated(EnumType.STRING)
    @Column(name = "circuit_breaker_state", nullable = false, length = 20)
    private CircuitBreakerState circuitBreakerState;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "dead_count", nullable = false)
    private int deadCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
