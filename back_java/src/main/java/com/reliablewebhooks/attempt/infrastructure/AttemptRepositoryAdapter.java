package com.reliablewebhooks.attempt.infrastructure;

import com.reliablewebhooks.attempt.domain.Attempt;
import com.reliablewebhooks.attempt.domain.AttemptRepository;
import lombok.RequiredArgsConstructor;
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
}
