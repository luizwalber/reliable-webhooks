package com.reliablewebhooks.outbox.application;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox: for each unpublished row, fans the Event out to every
 * registered Endpoint (one Delivery per Endpoint, docs/adr/0001-fan-out-and-delivery-resource),
 * publishes one Kafka message per Delivery (docs/adr/0004-retry-policy-and-topic-topology),
 * then marks the outbox row published and the Event PUBLISHED
 * (docs/adr/0003-transactional-outbox).
 *
 * DeliveryPublisher's implementation defers each send until this
 * transaction commits — see KafkaDeliveryPublisher's javadoc — so this
 * class just calls publish() directly per created Delivery, same as
 * every other DeliveryPublisher caller.
 *
 * Constructor is hand-written (not @RequiredArgsConstructor, see
 * .claude/lombok.mdc) because batchSize is @Value-injected.
 */
@Service
public class PublishOutboxEntriesUseCase {

    private final OutboxRepository outboxRepository;
    private final EventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryPublisher deliveryPublisher;
    private final int batchSize;
    private final boolean schedulerEnabled;

    public PublishOutboxEntriesUseCase(
            OutboxRepository outboxRepository,
            EventRepository eventRepository,
            EndpointRepository endpointRepository,
            DeliveryRepository deliveryRepository,
            DeliveryPublisher deliveryPublisher,
            @Value("${webhook.outbox.batch-size}") int batchSize,
            @Value("${webhook.background.outbox-poller.enabled:true}") boolean schedulerEnabled) {
        this.outboxRepository = outboxRepository;
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryPublisher = deliveryPublisher;
        this.batchSize = batchSize;
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * Off by default in the shared test context (webhook.background.outbox-poller.enabled=false,
     * see AbstractIntegrationTest's javadoc for the shared background-process
     * gating convention, spec issue #27) — every integration test ingests
     * events and registers endpoints, and fan-out targets EVERY registered
     * Endpoint (docs/adr/0001), so a live scheduled poller would fan any
     * test's event out to every other test's endpoints too, silently
     * inflating Delivery counts. Tests call pollOnce() directly instead
     * (see its own javadoc).
     */
    @Scheduled(fixedDelayString = "${webhook.outbox.poll-interval-ms}")
    public void poll() {
        if (!schedulerEnabled) {
            return;
        }
        pollOnce();
    }

    /** Exposed separately from the @Scheduled trigger so tests can call it deterministically instead of waiting on wall-clock timing. */
    @Transactional
    public int pollOnce() {
        List<OutboxEntry> batch = outboxRepository.findUnpublishedBatch(batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        // Fetched once per poll, not once per outbox entry — every entry in
        // the batch fans out to the same registered-Endpoint set.
        List<Endpoint> endpoints = endpointRepository.findAll();
        batch.forEach(entry -> publishEntry(entry, endpoints));
        return batch.size();
    }

    private void publishEntry(OutboxEntry entry, List<Endpoint> endpoints) {
        List<Delivery> createdDeliveries = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            createdDeliveries.add(deliveryRepository.save(Delivery.schedule(entry.getEventId(), endpoint.getId())));
        }

        entry.markPublished();
        outboxRepository.save(entry);

        eventRepository.findById(entry.getEventId()).ifPresent(event -> {
            event.markPublished();
            eventRepository.save(event);
        });

        createdDeliveries.forEach(delivery -> deliveryPublisher.publish(delivery, 1));
    }
}
