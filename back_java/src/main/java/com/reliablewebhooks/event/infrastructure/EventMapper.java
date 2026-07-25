package com.reliablewebhooks.event.infrastructure;

import com.reliablewebhooks.event.domain.Event;

final class EventMapper {

    private EventMapper() {
    }

    static EventJpaEntity toJpaEntity(Event event) {
        return new EventJpaEntity(
                event.getId(),
                event.getEventType(),
                event.getProducerId(),
                event.getIdempotencyKey(),
                event.getState(),
                event.getPayload(),
                event.getReceivedAt());
    }

    static Event toDomain(EventJpaEntity jpaEntity) {
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
