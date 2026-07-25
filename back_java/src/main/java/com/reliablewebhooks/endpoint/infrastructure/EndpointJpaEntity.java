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

@Entity
@Table(name = "endpoints")
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

    protected EndpointJpaEntity() {
        // JPA
    }

    public EndpointJpaEntity(UUID id, String url, String description, String secret,
                              CircuitBreakerState circuitBreakerState, int successCount, int deadCount,
                              OffsetDateTime createdAt) {
        this.id = id;
        this.url = url;
        this.description = description;
        this.secret = secret;
        this.circuitBreakerState = circuitBreakerState;
        this.successCount = successCount;
        this.deadCount = deadCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getSecret() {
        return secret;
    }

    public CircuitBreakerState getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getDeadCount() {
        return deadCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
