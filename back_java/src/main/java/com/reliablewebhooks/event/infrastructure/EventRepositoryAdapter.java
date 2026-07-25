package com.reliablewebhooks.event.infrastructure;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository springDataRepository;
    private final EventMapper eventMapper;

    @Override
    public Event save(Event event) {
        var saved = springDataRepository.save(eventMapper.toJpaEntity(event));
        return eventMapper.toDomain(saved);
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return springDataRepository.findById(id).map(eventMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    public long count() {
        return springDataRepository.count();
    }

    @Override
    public List<Event> findAll() {
        return springDataRepository.findAll().stream().map(eventMapper::toDomain).toList();
    }

    @Override
    public Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable) {
        return springDataRepository.findAllByOrderByReceivedAtDesc(pageable).map(eventMapper::toDomain);
    }
}
