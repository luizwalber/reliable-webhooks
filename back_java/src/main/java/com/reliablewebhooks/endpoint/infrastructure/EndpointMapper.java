package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.Endpoint;

final class EndpointMapper {

    private EndpointMapper() {
    }

    static EndpointJpaEntity toJpaEntity(Endpoint endpoint) {
        return new EndpointJpaEntity(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.getDescription(),
                endpoint.getSecret(),
                endpoint.getCircuitBreakerState(),
                endpoint.getSuccessCount(),
                endpoint.getDeadCount(),
                endpoint.getCreatedAt());
    }

    static Endpoint toDomain(EndpointJpaEntity jpaEntity) {
        return Endpoint.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getUrl(),
                jpaEntity.getDescription(),
                jpaEntity.getSecret(),
                jpaEntity.getCircuitBreakerState(),
                jpaEntity.getSuccessCount(),
                jpaEntity.getDeadCount(),
                jpaEntity.getCreatedAt());
    }
}
