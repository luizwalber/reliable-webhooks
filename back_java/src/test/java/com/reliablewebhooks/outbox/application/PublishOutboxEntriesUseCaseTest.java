package com.reliablewebhooks.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.event.domain.EventState;
import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import java.time.OffsetDateTime;
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

/** Plain-Java unit test against fake ports — no Spring context, no database, no Kafka. */
class PublishOutboxEntriesUseCaseTest {

    private final Map<UUID, OutboxEntry> outboxEntries = new HashMap<>();
    private final Map<UUID, Event> events = new HashMap<>();
    private final List<Endpoint> endpoints = new ArrayList<>();
    private final List<Delivery> savedDeliveries = new ArrayList<>();
    private final List<PublishedMessage> publishedMessages = new ArrayList<>();

    private final PublishOutboxEntriesUseCase useCase = new PublishOutboxEntriesUseCase(
            new FakeOutboxRepository(), new FakeEventRepository(), new FakeEndpointRepository(),
            new FakeDeliveryRepository(), new FakeDeliveryPublisher(), 50);

    @Test
    void fansOutToEveryRegisteredEndpointAndPublishesOneMessagePerDelivery() {
        Event event = givenAnOutboxedEvent();
        givenARegisteredEndpoint("https://example.com/a");
        givenARegisteredEndpoint("https://example.com/b");

        int processed = useCase.pollOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(savedDeliveries).hasSize(2);
        assertThat(savedDeliveries).allSatisfy(delivery -> {
            assertThat(delivery.getEventId()).isEqualTo(event.getId());
            assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
            assertThat(delivery.getAttemptCount()).isZero();
        });
        assertThat(publishedMessages).hasSize(2);
        assertThat(publishedMessages).allSatisfy(message -> assertThat(message.attemptNumber()).isEqualTo(1));
        assertThat(outboxEntries.values()).allSatisfy(entry -> assertThat(entry.isPublished()).isTrue());
        assertThat(events.get(event.getId()).getState()).isEqualTo(EventState.PUBLISHED);
    }

    @Test
    void anEventWithNoRegisteredEndpointsStillReachesPublishedWithNoDeliveries() {
        Event event = givenAnOutboxedEvent();

        int processed = useCase.pollOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(savedDeliveries).isEmpty();
        assertThat(publishedMessages).isEmpty();
        assertThat(events.get(event.getId()).getState()).isEqualTo(EventState.PUBLISHED);
        assertThat(outboxEntries.values()).allSatisfy(entry -> assertThat(entry.isPublished()).isTrue());
    }

    private Event givenAnOutboxedEvent() {
        Event event = Event.receive("order.created", "producer-1", UUID.randomUUID().toString(), Map.of());
        event.markOutboxed();
        events.put(event.getId(), event);
        OutboxEntry entry = OutboxEntry.forEvent(event.getId(), event.getPayload());
        outboxEntries.put(entry.getId(), entry);
        return event;
    }

    private void givenARegisteredEndpoint(String url) {
        endpoints.add(Endpoint.register(url, null, "secret"));
    }

    private record PublishedMessage(UUID deliveryId, int attemptNumber) {
    }

    private class FakeOutboxRepository implements OutboxRepository {
        @Override
        public OutboxEntry save(OutboxEntry entry) {
            outboxEntries.put(entry.getId(), entry);
            return entry;
        }

        @Override
        public List<OutboxEntry> findAll() {
            return List.copyOf(outboxEntries.values());
        }

        @Override
        public List<OutboxEntry> findUnpublishedBatch(int batchSize) {
            return outboxEntries.values().stream().filter(e -> !e.isPublished()).limit(batchSize).toList();
        }
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

    private class FakeEndpointRepository implements EndpointRepository {
        @Override
        public Endpoint save(Endpoint endpoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Endpoint> findAll() {
            return List.copyOf(endpoints);
        }
    }

    private class FakeDeliveryRepository implements DeliveryRepository {
        @Override
        public Delivery save(Delivery delivery) {
            savedDeliveries.add(delivery);
            return delivery;
        }

        @Override
        public Optional<Delivery> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }

    private class FakeDeliveryPublisher implements DeliveryPublisher {
        @Override
        public void publish(Delivery delivery, int attemptNumber) {
            publishedMessages.add(new PublishedMessage(delivery.getId(), attemptNumber));
        }

        @Override
        public boolean hasRetryBandFor(int attemptNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OffsetDateTime scheduleRetry(Delivery delivery, int attemptNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishToDeadLetter(Delivery delivery, int attemptNumber) {
            throw new UnsupportedOperationException();
        }
    }
}
