package com.reliablewebhooks.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListEventsUseCaseTest {

    @Test
    void returnsAPageOfEventViewsFromTheRepository() {
        Event first = Event.receive("order.created", "producer-1", "key-1", java.util.Map.of());
        Event second = Event.receive("order.updated", "producer-1", "key-2", java.util.Map.of());
        ListEventsUseCase useCase = new ListEventsUseCase(new FixedEventRepository(List.of(first, second)));

        Page<EventView> page = useCase.execute(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(EventView::id).containsExactly(first.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private static class FixedEventRepository implements EventRepository {
        private final List<Event> events;

        FixedEventRepository(List<Event> events) {
            this.events = events;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return events.size();
        }

        @Override
        public List<Event> findAll() {
            return events;
        }

        @Override
        public Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable) {
            return new PageImpl<>(events, pageable, events.size());
        }
    }
}
