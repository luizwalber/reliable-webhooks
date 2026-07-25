package com.reliablewebhooks.event.infrastructure;

import com.reliablewebhooks.event.domain.EventState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "events")
public class EventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "producer_id", nullable = false)
    private String producerId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventState state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected EventJpaEntity() {
        // JPA
    }

    public EventJpaEntity(UUID id, String eventType, String producerId, String idempotencyKey,
                           EventState state, Map<String, Object> payload, OffsetDateTime receivedAt) {
        this.id = id;
        this.eventType = eventType;
        this.producerId = producerId;
        this.idempotencyKey = idempotencyKey;
        this.state = state;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getProducerId() {
        return producerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public EventState getState() {
        return state;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }
}
