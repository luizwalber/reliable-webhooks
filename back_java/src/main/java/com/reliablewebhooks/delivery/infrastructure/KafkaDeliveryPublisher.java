package com.reliablewebhooks.delivery.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.RetryLadder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes to the main topic, the retry-band topics, and the DLQ topic
 * (docs/adr/0004-retry-policy-and-topic-topology). Key = endpointId, value
 * = DeliveryAttemptMessage as JSON. A retry-band message additionally
 * carries nextAttemptAt as a Kafka header — DeliveryAttemptConsumer reads
 * it and sleeps the consuming thread until due (the ADR's documented
 * demo-scale simplification). Which band an attempt retries onto and its
 * jittered delay are RetryLadder's job — this class only owns the Kafka
 * mechanics of actually sending.
 *
 * Every send defers until the current transaction commits, if one is
 * active: KafkaTemplate.send() doesn't participate in the surrounding
 * Postgres transaction, so a fast consumer could otherwise read a Delivery
 * by ID (or its updated state) before the write that produced it is even
 * visible. Owning this here — not in each caller — means every use case
 * that publishes gets the guarantee automatically, rather than needing to
 * remember it per call site.
 *
 * Constructor is hand-written (not @RequiredArgsConstructor, see
 * .claude/lombok.mdc) because mainTopic/dlqTopic are @Value-injected.
 */
@Component
class KafkaDeliveryPublisher implements DeliveryPublisher {

    static final String NEXT_ATTEMPT_AT_HEADER = "nextAttemptAt";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String mainTopic;
    private final String dlqTopic;
    private final RetryLadder retryLadder;

    KafkaDeliveryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${webhook.kafka.main-topic}") String mainTopic,
            @Value("${webhook.kafka.dlq-topic}") String dlqTopic,
            RetryTopology retryTopology) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.mainTopic = mainTopic;
        this.dlqTopic = dlqTopic;
        this.retryLadder = new RetryLadder(
                retryTopology.bands().stream().map(band -> new RetryLadder.Band(band.topic(), band.delayMs())).toList(),
                retryTopology.jitter());
    }

    @Override
    public void publish(Delivery delivery, int attemptNumber) {
        send(mainTopic, delivery, attemptNumber, null);
    }

    @Override
    public boolean hasRetryBandFor(int attemptNumber) {
        return retryLadder.hasNextBand(attemptNumber);
    }

    @Override
    public OffsetDateTime scheduleRetry(Delivery delivery, int attemptNumber) {
        RetryLadder.NextAttempt nextAttempt = retryLadder.nextAttempt(attemptNumber);
        send(nextAttempt.topic(), delivery, attemptNumber, nextAttempt.nextAttemptAt());
        return nextAttempt.nextAttemptAt();
    }

    @Override
    public void publishToDeadLetter(Delivery delivery, int attemptNumber) {
        send(dlqTopic, delivery, attemptNumber, null);
    }

    private void send(String topic, Delivery delivery, int attemptNumber, OffsetDateTime nextAttemptAt) {
        var message = new DeliveryAttemptMessage(delivery.getId(), delivery.getEventId(), delivery.getEndpointId(), attemptNumber);
        var record = new ProducerRecord<>(topic, delivery.getEndpointId().toString(), writeValueAsString(message));
        if (nextAttemptAt != null) {
            record.headers().add(NEXT_ATTEMPT_AT_HEADER, nextAttemptAt.toString().getBytes(StandardCharsets.UTF_8));
        }
        sendAfterCommit(record);
    }

    private void sendAfterCommit(ProducerRecord<String, String> record) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            kafkaTemplate.send(record);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(record);
            }
        });
    }

    private String writeValueAsString(DeliveryAttemptMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize delivery-attempt message", e);
        }
    }
}
