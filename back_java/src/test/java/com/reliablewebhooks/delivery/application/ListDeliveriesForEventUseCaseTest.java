package com.reliablewebhooks.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListDeliveriesForEventUseCaseTest {

    @Test
    void throwsNotFoundWhenTheEventDoesNotExist() {
        ListDeliveriesForEventUseCase useCase = new ListDeliveriesForEventUseCase(
                new EventRepositoryStub(false), new EmptyDeliveryRepository());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsAnEmptyPageWhenTheEventExistsButHasNoDeliveriesYet() {
        ListDeliveriesForEventUseCase useCase = new ListDeliveriesForEventUseCase(
                new EventRepositoryStub(true), new EmptyDeliveryRepository());

        Page<DeliveryView> page = useCase.execute(UUID.randomUUID(), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    private static class EventRepositoryStub implements EventRepository {
        private final boolean exists;

        EventRepositoryStub(boolean exists) {
            this.exists = exists;
        }

        @Override
        public Event save(Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Event> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            return exists;
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Event> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }

    private static class EmptyDeliveryRepository implements DeliveryRepository {
        @Override
        public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
            return new PageImpl<>(List.of());
        }
    }
}
