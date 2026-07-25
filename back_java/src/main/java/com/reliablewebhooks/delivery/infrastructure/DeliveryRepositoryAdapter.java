package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
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
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
        return springDataRepository.findByEventId(eventId, pageable).map(deliveryMapper::toDomain);
    }

    @Override
    public Page<Delivery> search(DeliveryState state, UUID endpointId, Pageable pageable) {
        Page<DeliveryJpaEntity> page;
        if (state != null && endpointId != null) {
            page = springDataRepository.findByStateAndEndpointId(state, endpointId, pageable);
        } else if (state != null) {
            page = springDataRepository.findByState(state, pageable);
        } else if (endpointId != null) {
            page = springDataRepository.findByEndpointId(endpointId, pageable);
        } else {
            page = springDataRepository.findAll(pageable);
        }
        return page.map(deliveryMapper::toDomain);
    }
}
