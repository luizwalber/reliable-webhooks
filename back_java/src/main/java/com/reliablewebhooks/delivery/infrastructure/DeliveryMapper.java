package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.Delivery;
import org.mapstruct.Mapper;

/**
 * domain → JPA is MapStruct-generated (property-name matched onto
 * DeliveryJpaEntity's setters). JPA → domain stays hand-written, delegating
 * to Delivery.reconstitute() so the domain's controlled-construction
 * factory — not MapStruct — remains the single place that assembles a
 * Delivery. See .claude/mapstruct.mdc.
 */
@Mapper(componentModel = "spring")
interface DeliveryMapper {

    DeliveryJpaEntity toJpaEntity(Delivery delivery);

    default Delivery toDomain(DeliveryJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return Delivery.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getEventId(),
                jpaEntity.getEndpointId(),
                jpaEntity.getState(),
                jpaEntity.getAttemptCount(),
                jpaEntity.getNextAttemptAt(),
                jpaEntity.getCreatedAt(),
                jpaEntity.getUpdatedAt());
    }
}
