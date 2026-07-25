package com.reliablewebhooks.attempt.infrastructure;

import com.reliablewebhooks.attempt.domain.Attempt;
import org.mapstruct.Mapper;

/**
 * domain → JPA is MapStruct-generated. JPA → domain stays hand-written,
 * delegating to Attempt.reconstitute() — see .claude/mapstruct.mdc.
 */
@Mapper(componentModel = "spring")
interface AttemptMapper {

    AttemptJpaEntity toJpaEntity(Attempt attempt);

    default Attempt toDomain(AttemptJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return Attempt.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getDeliveryId(),
                jpaEntity.getAttemptNumber(),
                jpaEntity.getOutcome(),
                jpaEntity.getHttpStatusCode(),
                jpaEntity.getTopic(),
                jpaEntity.getStartedAt(),
                jpaEntity.getFinishedAt());
    }
}
