package com.reliablewebhooks.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.event.domain.EventState;
import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Plain-Java unit test against fake ports — no Spring context, no database. */
class IngestEventUseCaseTest {

    private final Map<UUID, Event> events = new HashMap<>();
    private final List<OutboxEntry> outboxEntries = new ArrayList<>();

    private final IngestEventUseCase useCase = new IngestEventUseCase(new FakeEventRepository(), new FakeOutboxRepository());

    @Test
    void writesTheEventAndItsOutboxRowAndReturnsAnOutboxedView() {
        EventView view = useCase.execute(new IngestEventCommand("producer-1", "key-1", "order.created", Map.of("orderId", "o-1")));

        assertThat(view.state()).isEqualTo(EventState.OUTBOXED.name());
        assertThat(events.get(view.id()).getState()).isEqualTo(EventState.OUTBOXED);
        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).getEventId()).isEqualTo(view.id());
    }

    private class FakeEventRepository implements EventRepository {
        @Override
        public Event save(Event event) {
            events.put(event.getId(), event);
            return event;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.ofNullable(events.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return events.containsKey(id);
        }

        @Override
        public long count() {
            return events.size();
        }

        @Override
        public List<Event> findAll() {
            return List.copyOf(events.values());
        }

        @Override
        public Page<Event> findAllOrderByReceivedAtDesc(Pageable pageable) {
            return new PageImpl<>(findAll());
        }
    }

    private class FakeOutboxRepository implements OutboxRepository {
        @Override
        public OutboxEntry save(OutboxEntry entry) {
            outboxEntries.add(entry);
            return entry;
        }

        @Override
        public List<OutboxEntry> findAll() {
            return List.copyOf(outboxEntries);
        }

        @Override
        public List<OutboxEntry> findUnpublishedBatch(int batchSize) {
            return outboxEntries.stream().filter(e -> !e.isPublished()).limit(batchSize).toList();
        }
    }
}
