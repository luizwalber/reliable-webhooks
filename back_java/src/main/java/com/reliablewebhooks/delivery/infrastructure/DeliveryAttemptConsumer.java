package com.reliablewebhooks.delivery.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.delivery.application.DeliveryAttemptCommand;
import com.reliablewebhooks.delivery.application.ProcessDeliveryAttemptUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapts the Kafka wire format into ProcessDeliveryAttemptUseCase's
 * command: deserializes the message, and — for a retry-band message —
 * sleeps the consuming thread until the nextAttemptAt header is due before
 * handing off (docs/adr/0004-retry-policy-and-topic-topology's documented
 * demo-scale simplification; no such header on the main topic).
 *
 * Gated by webhook.delivery-worker.enabled (default true) so integration
 * tests that don't need a live worker aren't racing a real consumer
 * against every event any other test class fans out in the shared
 * persistent test database (docs/adr/0014-docker-compose-test-seam) —
 * DeliveryWorkerTest is the one test class that opts back in.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "webhook.delivery-worker", name = "enabled", havingValue = "true", matchIfMissing = true)
class DeliveryAttemptConsumer {

    private final ProcessDeliveryAttemptUseCase useCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "#{'${webhook.kafka.consumed-topics}'.split(',')}", groupId = "delivery-worker")
    public void onMessage(ConsumerRecord<String, String> record) {
        DeliveryAttemptMessage message = readValue(record.value());
        sleepUntilDue(record);
        useCase.execute(new DeliveryAttemptCommand(
                message.deliveryId(), message.eventId(), message.endpointId(), message.attemptNumber(), record.topic()));
    }

    private void sleepUntilDue(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaDeliveryPublisher.NEXT_ATTEMPT_AT_HEADER);
        if (header == null) {
            return;
        }
        OffsetDateTime nextAttemptAt = OffsetDateTime.parse(new String(header.value(), StandardCharsets.UTF_8));
        long millisUntilDue = Duration.between(OffsetDateTime.now(), nextAttemptAt).toMillis();
        if (millisUntilDue <= 0) {
            return;
        }
        try {
            Thread.sleep(millisUntilDue);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for nextAttemptAt", e);
        }
    }

    private DeliveryAttemptMessage readValue(String json) {
        try {
            return objectMapper.readValue(json, DeliveryAttemptMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize delivery-attempt message", e);
        }
    }
}
