package com.reliablewebhooks.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListDeliveriesUseCaseTest {

    @Test
    void passesTheStateAndEndpointIdFiltersThroughToTheRepository() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        RecordingDeliveryRepository repository = new RecordingDeliveryRepository(List.of(delivery));
        ListDeliveriesUseCase useCase = new ListDeliveriesUseCase(repository);
        UUID endpointId = delivery.getEndpointId();

        Page<DeliveryView> page = useCase.execute(DeliveryState.DEAD, endpointId, PageRequest.of(0, 10));

        assertThat(repository.lastState).isEqualTo(DeliveryState.DEAD);
        assertThat(repository.lastEndpointId).isEqualTo(endpointId);
        assertThat(page.getContent()).extracting(DeliveryView::id).containsExactly(delivery.getId());
    }

    @Test
    void bothFiltersAreOptional() {
        RecordingDeliveryRepository repository = new RecordingDeliveryRepository(List.of());
        ListDeliveriesUseCase useCase = new ListDeliveriesUseCase(repository);

        useCase.execute(null, null, PageRequest.of(0, 10));

        assertThat(repository.lastState).isNull();
        assertThat(repository.lastEndpointId).isNull();
    }

    private static class RecordingDeliveryRepository implements DeliveryRepository {
        private final List<Delivery> deliveries;
        private DeliveryState lastState;
        private UUID lastEndpointId;

        RecordingDeliveryRepository(List<Delivery> deliveries) {
            this.deliveries = deliveries;
        }

        @Override
        public Delivery save(Delivery delivery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Delivery> findById(UUID id) {
            throw new UnsupportedOperationException();
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
            this.lastState = state;
            this.lastEndpointId = endpointId;
            return new PageImpl<>(deliveries, pageable, deliveries.size());
        }
    }
}
