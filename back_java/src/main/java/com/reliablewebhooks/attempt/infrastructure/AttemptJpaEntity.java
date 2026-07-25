package com.reliablewebhooks.attempt.infrastructure;

import com.reliablewebhooks.attempt.domain.AttemptOutcome;
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
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttemptJpaEntity {

    @Id
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AttemptOutcome outcome;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(length = 255)
    private String topic;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
