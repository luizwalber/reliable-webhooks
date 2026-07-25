package com.reliablewebhooks.delivery.application;

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
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointCircuitBreaker;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes one Delivery-attempt message: dedup check, circuit-breaker
 * permission, the real signed HTTP call, Attempt recording, and the
 * resulting Delivery/Endpoint state transition — including republishing to
 * the next retry-band topic or the DLQ (docs/adr/0002, 0004, 0005, 0006,
 * 0007, 0009). Driven by delivery.infrastructure.DeliveryAttemptConsumer's
 * @KafkaListener.
 */
@Service
@RequiredArgsConstructor
public class ProcessDeliveryAttemptUseCase {

    private final DeliveryRepository deliveryRepository;
    private final EventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final AttemptRepository attemptRepository;
    private final DeliveryDedupChecker dedupChecker;
    private final EndpointCircuitBreaker circuitBreaker;
    private final EndpointDeliveryClient deliveryClient;
    private final DeliveryPublisher deliveryPublisher;

    @Transactional
    public void execute(DeliveryAttemptCommand command) {
        Delivery delivery = deliveryRepository.findById(command.deliveryId())
                .orElseThrow(() -> new IllegalStateException("Delivery not found: " + command.deliveryId()));
        Endpoint endpoint = endpointRepository.findById(command.endpointId())
                .orElseThrow(() -> new IllegalStateException("Endpoint not found: " + command.endpointId()));

        if (delivery.getState() == DeliveryState.DELIVERED || dedupChecker.isMarkedDelivered(command.eventId(), command.endpointId())) {
            recordSyntheticAttempt(delivery, command, AttemptOutcome.DEDUPED);
            return;
        }

        if (!circuitBreaker.tryAcquirePermission(command.endpointId())) {
            recordSyntheticAttempt(delivery, command, AttemptOutcome.CIRCUIT_OPEN);
            handleFailure(delivery, endpoint, command);
            return;
        }

        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new IllegalStateException("Event not found: " + command.eventId()));

        delivery.startDelivering();
        deliveryRepository.save(delivery);

        Attempt attempt = Attempt.start(delivery.getId(), command.attemptNumber(), command.topic());
        attemptRepository.save(attempt);

        DeliveryAttemptResult result = deliveryClient.deliver(endpoint, event.getPayload(), delivery.getId());
        attempt.resolve(result.outcome(), result.httpStatusCode());
        attemptRepository.save(attempt);

        if (result.outcome() == AttemptOutcome.SUCCESS) {
            circuitBreaker.recordSuccess(command.endpointId());
            delivery.markDelivered();
            deliveryRepository.save(delivery);
            dedupChecker.markDelivered(command.eventId(), command.endpointId());
            endpoint.recordSuccess();
            endpoint.updateCircuitBreakerState(circuitBreaker.currentState(command.endpointId()));
            endpointRepository.save(endpoint);
            return;
        }

        circuitBreaker.recordFailure(command.endpointId());
        handleFailure(delivery, endpoint, command);
    }

    private void recordSyntheticAttempt(Delivery delivery, DeliveryAttemptCommand command, AttemptOutcome outcome) {
        Attempt attempt = Attempt.start(delivery.getId(), command.attemptNumber(), command.topic());
        attempt.resolve(outcome, null);
        attemptRepository.save(attempt);
    }

    private void handleFailure(Delivery delivery, Endpoint endpoint, DeliveryAttemptCommand command) {
        endpoint.updateCircuitBreakerState(circuitBreaker.currentState(command.endpointId()));
        int nextAttemptNumber = command.attemptNumber() + 1;
        if (deliveryPublisher.hasRetryBandFor(nextAttemptNumber)) {
            OffsetDateTime nextAttemptAt = deliveryPublisher.scheduleRetry(delivery, nextAttemptNumber);
            delivery.scheduleRetry(nextAttemptAt);
            deliveryRepository.save(delivery);
            endpointRepository.save(endpoint);
        } else {
            delivery.markDead();
            deliveryRepository.save(delivery);
            endpoint.recordFailure();
            endpointRepository.save(endpoint);
            deliveryPublisher.publishToDeadLetter(delivery, command.attemptNumber());
        }
    }
}
