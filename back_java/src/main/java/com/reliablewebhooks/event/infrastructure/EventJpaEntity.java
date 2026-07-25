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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Setters exist for MapStruct's no-arg-constructor-plus-setters mapping strategy — see .claude/mapstruct.mdc. */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
}
