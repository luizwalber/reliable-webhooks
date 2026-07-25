package com.reliablewebhooks.outbox.infrastructure;

import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutboxRepositoryAdapter implements OutboxRepository {

    private final SpringDataOutboxRepository springDataRepository;

    @Override
    public OutboxEntry save(OutboxEntry entry) {
        var saved = springDataRepository.save(OutboxMapper.toJpaEntity(entry));
        return OutboxMapper.toDomain(saved);
    }

    @Override
    public List<OutboxEntry> findAll() {
        return springDataRepository.findAll().stream().map(OutboxMapper::toDomain).toList();
    }
}
