package com.reliablewebhooks.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class GetDeliveryUseCaseTest {

    private final Map<UUID, Delivery> deliveries = new HashMap<>();
    private final GetDeliveryUseCase useCase = new GetDeliveryUseCase(new FakeDeliveryRepository());

    @Test
    void throwsNotFoundForAnUnknownDeliveryId() {
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsAViewForAKnownDelivery() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        deliveries.put(delivery.getId(), delivery);

        DeliveryView view = useCase.execute(delivery.getId());

        assertThat(view.id()).isEqualTo(delivery.getId());
        assertThat(view.state()).isEqualTo(DeliveryState.SCHEDULED.name());
    }

    private class FakeDeliveryRepository implements DeliveryRepository {
        @Override
        public Delivery save(Delivery delivery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Delivery> findById(UUID id) {
            return Optional.ofNullable(deliveries.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Delivery> search(DeliveryState state, UUID endpointId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }
}
