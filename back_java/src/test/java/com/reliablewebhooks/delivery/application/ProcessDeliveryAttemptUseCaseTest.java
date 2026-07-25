package com.reliablewebhooks.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.attempt.domain.Attempt;
import com.reliablewebhooks.attempt.domain.AttemptOutcome;
import com.reliablewebhooks.attempt.domain.AttemptRepository;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryAttemptResult;
import com.reliablewebhooks.delivery.domain.DeliveryDedupChecker;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.delivery.domain.EndpointDeliveryClient;
import com.reliablewebhooks.endpoint.domain.CircuitBreakerState;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointCircuitBreaker;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Plain-Java unit test against fake ports — no Spring context, no database, no Kafka, no real HTTP. */
class ProcessDeliveryAttemptUseCaseTest {

    private final Map<UUID, Delivery> deliveries = new HashMap<>();
    private final Map<UUID, Event> events = new HashMap<>();
    private final Map<UUID, Endpoint> endpoints = new HashMap<>();
    private final List<Attempt> savedAttempts = new ArrayList<>();
    private final Set<String> dedupMarkers = new HashSet<>();
    private final FakeEndpointCircuitBreaker circuitBreaker = new FakeEndpointCircuitBreaker();
    private final FakeEndpointDeliveryClient deliveryClient = new FakeEndpointDeliveryClient();
    private final FakeDeliveryPublisher deliveryPublisher = new FakeDeliveryPublisher();

    private final ProcessDeliveryAttemptUseCase useCase = new ProcessDeliveryAttemptUseCase(
            new FakeDeliveryRepository(), new FakeEventRepository(), new FakeEndpointRepository(),
            new FakeAttemptRepository(), new FakeDeliveryDedupChecker(), circuitBreaker, deliveryClient, deliveryPublisher);

    @Test
    void successfulAttemptMarksDeliveredAndIncrementsSuccessCount() {
        Delivery delivery = givenAScheduledDelivery();
        Endpoint endpoint = endpoints.get(delivery.getEndpointId());
        deliveryClient.result = new DeliveryAttemptResult(AttemptOutcome.SUCCESS, 200);

        useCase.execute(command(delivery, 1));

        assertThat(deliveries.get(delivery.getId()).getState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(endpoints.get(endpoint.getId()).getSuccessCount()).isEqualTo(1);
        assertThat(savedAttempts).hasSize(2); // in-flight save, then resolved save
        assertThat(savedAttempts.get(1).getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(circuitBreaker.successesRecorded).containsExactly(endpoint.getId());
        assertThat(deliveryPublisher.scheduleRetryCalls).isEmpty();
        assertThat(deliveryPublisher.deadLetterCalls).isEmpty();
    }

    @Test
    void retryableFailureWithBudgetRemainingReschedulesAndRepublishes() {
        Delivery delivery = givenAScheduledDelivery();
        deliveryClient.result = new DeliveryAttemptResult(AttemptOutcome.HTTP_5XX, 500);

        useCase.execute(command(delivery, 1));

        Delivery updated = deliveries.get(delivery.getId());
        assertThat(updated.getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(deliveryPublisher.scheduleRetryCalls).containsExactly(2);
        assertThat(deliveryPublisher.deadLetterCalls).isEmpty();
    }

    @Test
    void failureExhaustingBudgetGoesDeadAndPublishesToDlq() {
        Delivery delivery = givenAScheduledDelivery();
        Endpoint endpoint = endpoints.get(delivery.getEndpointId());
        deliveryClient.result = new DeliveryAttemptResult(AttemptOutcome.TIMEOUT, null);
        deliveryPublisher.maxAttempts = 1; // no retry bands left after attempt 1

        useCase.execute(command(delivery, 1));

        Delivery updated = deliveries.get(delivery.getId());
        assertThat(updated.getState()).isEqualTo(DeliveryState.DEAD);
        assertThat(endpoints.get(endpoint.getId()).getDeadCount()).isEqualTo(1);
        assertThat(deliveryPublisher.deadLetterCalls).containsExactly(1);
        assertThat(deliveryPublisher.scheduleRetryCalls).isEmpty();
    }

    @Test
    void dedupHitRecordsDedupedAttemptAndMakesNoHttpCall() {
        Delivery delivery = givenAScheduledDelivery();
        delivery.markDelivered(); // simulate an earlier successful attempt already landed

        useCase.execute(command(delivery, 2));

        assertThat(savedAttempts).hasSize(1);
        assertThat(savedAttempts.get(0).getOutcome()).isEqualTo(AttemptOutcome.DEDUPED);
        assertThat(deliveryClient.callCount).isZero();
    }

    @Test
    void circuitOpenRecordsCircuitOpenAttemptConsumesBudgetAndMakesNoHttpCall() {
        Delivery delivery = givenAScheduledDelivery();
        circuitBreaker.permissionGranted = false;

        useCase.execute(command(delivery, 1));

        assertThat(savedAttempts).hasSize(1);
        assertThat(savedAttempts.get(0).getOutcome()).isEqualTo(AttemptOutcome.CIRCUIT_OPEN);
        assertThat(deliveryClient.callCount).isZero();
        assertThat(deliveries.get(delivery.getId()).getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(deliveryPublisher.scheduleRetryCalls).containsExactly(2);
    }

    private Delivery givenAScheduledDelivery() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", null, "secret");
        endpoints.put(endpoint.getId(), endpoint);
        Event event = Event.receive("order.created", "producer-1", UUID.randomUUID().toString(), Map.of("orderId", "o-1"));
        events.put(event.getId(), event);
        Delivery delivery = Delivery.schedule(event.getId(), endpoint.getId());
        deliveries.put(delivery.getId(), delivery);
        return delivery;
    }

    private DeliveryAttemptCommand command(Delivery delivery, int attemptNumber) {
        return new DeliveryAttemptCommand(delivery.getId(), delivery.getEventId(), delivery.getEndpointId(), attemptNumber, "webhook.delivery.main");
    }

    private class FakeDeliveryRepository implements DeliveryRepository {
        @Override
        public Delivery save(Delivery delivery) {
            deliveries.put(delivery.getId(), delivery);
            return delivery;
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
        public Page<Delivery> search(com.reliablewebhooks.delivery.domain.DeliveryState state, UUID endpointId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }

    private class FakeEventRepository implements EventRepository {
        @Override
        public Event save(Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.ofNullable(events.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
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

    private class FakeEndpointRepository implements EndpointRepository {
        @Override
        public Endpoint save(Endpoint endpoint) {
            endpoints.put(endpoint.getId(), endpoint);
            return endpoint;
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            return Optional.ofNullable(endpoints.get(id));
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
            throw new UnsupportedOperationException();
        }
    }

    private class FakeAttemptRepository implements AttemptRepository {
        @Override
        public Attempt save(Attempt attempt) {
            savedAttempts.add(attempt);
            return attempt;
        }

        @Override
        public org.springframework.data.domain.Page<Attempt> findByDeliveryId(UUID deliveryId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }

    private class FakeDeliveryDedupChecker implements DeliveryDedupChecker {
        @Override
        public boolean isMarkedDelivered(UUID eventId, UUID endpointId) {
            return dedupMarkers.contains(eventId + ":" + endpointId);
        }

        @Override
        public void markDelivered(UUID eventId, UUID endpointId) {
            dedupMarkers.add(eventId + ":" + endpointId);
        }
    }

    private static class FakeEndpointCircuitBreaker implements EndpointCircuitBreaker {
        private boolean permissionGranted = true;
        private final List<UUID> successesRecorded = new ArrayList<>();
        private final List<UUID> failuresRecorded = new ArrayList<>();

        @Override
        public boolean tryAcquirePermission(UUID endpointId) {
            return permissionGranted;
        }

        @Override
        public void recordSuccess(UUID endpointId) {
            successesRecorded.add(endpointId);
        }

        @Override
        public void recordFailure(UUID endpointId) {
            failuresRecorded.add(endpointId);
        }

        @Override
        public CircuitBreakerState currentState(UUID endpointId) {
            return permissionGranted ? CircuitBreakerState.CLOSED : CircuitBreakerState.OPEN;
        }
    }

    private static class FakeEndpointDeliveryClient implements EndpointDeliveryClient {
        private DeliveryAttemptResult result;
        private int callCount;

        @Override
        public DeliveryAttemptResult deliver(Endpoint endpoint, Map<String, Object> eventPayload, UUID deliveryId) {
            callCount++;
            return result;
        }
    }

    /** Mimics a 1-band retry ladder: attempt 2 has a band, attempt 3+ doesn't, unless maxAttempts is widened. */
    private static class FakeDeliveryPublisher implements DeliveryPublisher {
        private int maxAttempts = 4;
        private final List<Integer> scheduleRetryCalls = new ArrayList<>();
        private final List<Integer> deadLetterCalls = new ArrayList<>();

        @Override
        public void publish(Delivery delivery, int attemptNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasRetryBandFor(int attemptNumber) {
            return attemptNumber <= maxAttempts;
        }

        @Override
        public OffsetDateTime scheduleRetry(Delivery delivery, int attemptNumber) {
            scheduleRetryCalls.add(attemptNumber);
            return OffsetDateTime.now().plusSeconds(10);
        }

        @Override
        public void publishToDeadLetter(Delivery delivery, int attemptNumber) {
            deadLetterCalls.add(attemptNumber);
        }
    }
}
