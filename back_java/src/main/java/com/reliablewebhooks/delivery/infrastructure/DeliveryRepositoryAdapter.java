package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeliveryRepositoryAdapter implements DeliveryRepository {

    private final SpringDataDeliveryRepository springDataRepository;
    private final DeliveryMapper deliveryMapper;

    @Override
    public Delivery save(Delivery delivery) {
        var saved = springDataRepository.save(deliveryMapper.toJpaEntity(delivery));
        return deliveryMapper.toDomain(saved);
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
        return springDataRepository.findById(id).map(deliveryMapper::toDomain);
    }

    @Override
    public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
        return springDataRepository.findByEventId(eventId, pageable).map(deliveryMapper::toDomain);
    }
}
