package com.reliablewebhooks.outbox.infrastructure;

import com.reliablewebhooks.outbox.domain.OutboxEntry;
import org.mapstruct.Mapper;

/**
 * domain → JPA is MapStruct-generated (property-name matched onto
 * OutboxJpaEntity's setters). JPA → domain stays hand-written, delegating
 * to OutboxEntry.reconstitute() so the domain's controlled-construction
 * factory — not MapStruct — remains the single place that assembles an
 * OutboxEntry. See .claude/mapstruct.mdc.
 */
@Mapper(componentModel = "spring")
interface OutboxMapper {

    OutboxJpaEntity toJpaEntity(OutboxEntry entry);

    default OutboxEntry toDomain(OutboxJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return OutboxEntry.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getEventId(),
                jpaEntity.getPayload(),
                jpaEntity.isPublished(),
                jpaEntity.getCreatedAt(),
                jpaEntity.getPublishedAt());
    }
}
