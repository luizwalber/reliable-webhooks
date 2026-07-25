package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.Delivery;

final class DeliveryMapper {

    private DeliveryMapper() {
    }

    static Delivery toDomain(DeliveryJpaEntity jpaEntity) {
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
