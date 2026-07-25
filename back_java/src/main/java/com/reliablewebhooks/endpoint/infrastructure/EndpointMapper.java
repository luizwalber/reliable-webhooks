package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import org.mapstruct.Mapper;

/**
 * domain → JPA is MapStruct-generated (property-name matched onto
 * EndpointJpaEntity's setters). JPA → domain stays hand-written, delegating
 * to Endpoint.reconstitute() so the domain's controlled-construction
 * factory — not MapStruct — remains the single place that assembles an
 * Endpoint. See .claude/mapstruct.mdc.
 */
@Mapper(componentModel = "spring")
interface EndpointMapper {

    EndpointJpaEntity toJpaEntity(Endpoint endpoint);

    default Endpoint toDomain(EndpointJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
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
