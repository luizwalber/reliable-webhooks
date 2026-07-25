package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EndpointRepositoryAdapter implements EndpointRepository {

    private final SpringDataEndpointRepository springDataRepository;

    @Override
    public Endpoint save(Endpoint endpoint) {
        var saved = springDataRepository.save(EndpointMapper.toJpaEntity(endpoint));
        return EndpointMapper.toDomain(saved);
    }

    @Override
    public Optional<Endpoint> findById(UUID id) {
        return springDataRepository.findById(id).map(EndpointMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable) {
        return springDataRepository.findAllByOrderByCreatedAtDesc(pageable).map(EndpointMapper::toDomain);
    }
}
