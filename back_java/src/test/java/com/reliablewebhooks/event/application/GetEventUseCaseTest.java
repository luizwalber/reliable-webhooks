package com.reliablewebhooks.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.event.domain.EventState;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class GetEventUseCaseTest {

    @Test
    void throwsNotFoundForAnUnknownEventId() {
        GetEventUseCase useCase = new GetEventUseCase(new EmptyEventRepository());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsAViewForAKnownEvent() {
        Event event = Event.receive("order.created", "producer-1", "key-1", java.util.Map.of());
        GetEventUseCase useCase = new GetEventUseCase(new SingleEventRepository(event));

        EventView view = useCase.execute(event.getId());

        assertThat(view.id()).isEqualTo(event.getId());
        assertThat(view.state()).isEqualTo(EventState.RECEIVED.name());
    }

    private static class EmptyEventRepository implements EventRepository {
        @Override
        public Event save(Event event) {
            return event;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(UUID id) {
            return false;
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public List<Event> findAll() {
            return List.of();
        }

        @Override
        public Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable) {
            return new PageImpl<>(List.of());
        }
    }

    private static class SingleEventRepository extends EmptyEventRepository {
        private final Event event;

        SingleEventRepository(Event event) {
            this.event = event;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return id.equals(event.getId()) ? Optional.of(event) : Optional.empty();
        }
    }
}
