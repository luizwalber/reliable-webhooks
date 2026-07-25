package com.reliablewebhooks.attempt.infrastructure;

import com.reliablewebhooks.attempt.domain.Attempt;
import com.reliablewebhooks.attempt.domain.AttemptRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AttemptRepositoryAdapter implements AttemptRepository {

    private final SpringDataAttemptRepository springDataRepository;
    private final AttemptMapper attemptMapper;

    @Override
    public Attempt save(Attempt attempt) {
        var saved = springDataRepository.save(attemptMapper.toJpaEntity(attempt));
        return attemptMapper.toDomain(saved);
    }

    @Override
    public Page<Attempt> findByDeliveryId(UUID deliveryId, Pageable pageable) {
        return springDataRepository.findByDeliveryIdOrderByStartedAtAsc(deliveryId, pageable).map(attemptMapper::toDomain);
    }
}
