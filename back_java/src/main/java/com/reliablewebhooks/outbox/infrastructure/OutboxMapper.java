package com.reliablewebhooks.outbox.infrastructure;

import com.reliablewebhooks.outbox.domain.OutboxEntry;

final class OutboxMapper {

    private OutboxMapper() {
    }

    static OutboxJpaEntity toJpaEntity(OutboxEntry entry) {
        return new OutboxJpaEntity(
                entry.getId(),
                entry.getEventId(),
                entry.getPayload(),
                entry.isPublished(),
                entry.getCreatedAt(),
                entry.getPublishedAt());
    }

    static OutboxEntry toDomain(OutboxJpaEntity jpaEntity) {
        return OutboxEntry.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getEventId(),
                jpaEntity.getPayload(),
                jpaEntity.isPublished(),
                jpaEntity.getCreatedAt(),
                jpaEntity.getPublishedAt());
    }
}
