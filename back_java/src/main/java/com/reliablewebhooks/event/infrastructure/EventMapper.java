package com.reliablewebhooks.event.infrastructure;

import com.reliablewebhooks.event.domain.Event;
import org.mapstruct.Mapper;

/**
 * domain → JPA is MapStruct-generated (property-name matched onto
 * EventJpaEntity's setters). JPA → domain stays hand-written, delegating to
 * Event.reconstitute() so the domain's controlled-construction factory —
 * not MapStruct — remains the single place that assembles an Event. See
 * .claude/mapstruct.mdc.
 */
@Mapper(componentModel = "spring")
interface EventMapper {

    EventJpaEntity toJpaEntity(Event event);

    default Event toDomain(EventJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return Event.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getEventType(),
                jpaEntity.getProducerId(),
                jpaEntity.getIdempotencyKey(),
                jpaEntity.getPayload(),
                jpaEntity.getState(),
                jpaEntity.getReceivedAt());
    }
}
