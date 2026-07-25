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
    private final OutboxMapper outboxMapper;

    @Override
    public OutboxEntry save(OutboxEntry entry) {
        var saved = springDataRepository.save(outboxMapper.toJpaEntity(entry));
        return outboxMapper.toDomain(saved);
    }

    @Override
    public List<OutboxEntry> findAll() {
        return springDataRepository.findAll().stream().map(outboxMapper::toDomain).toList();
    }

    @Override
    public List<OutboxEntry> findUnpublishedBatch(int batchSize) {
        return springDataRepository.findUnpublishedBatchForUpdateSkipLocked(batchSize).stream()
                .map(outboxMapper::toDomain)
                .toList();
    }
}
