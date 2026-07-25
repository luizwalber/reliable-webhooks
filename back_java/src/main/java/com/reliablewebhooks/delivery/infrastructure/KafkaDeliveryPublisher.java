package com.reliablewebhooks.delivery.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to webhook.delivery.main only — the other four topics from
 * docs/adr/0004-retry-policy-and-topic-topology don't exist yet; nothing
 * publishes to or consumes from them until the delivery-workers slice.
 * Key = endpointId, value = DeliveryAttemptMessage as JSON, per the ADR.
 *
 * Constructor is hand-written (not @RequiredArgsConstructor, see
 * .claude/lombok.mdc) because mainTopic is @Value-injected — Lombok's
 * generated constructor has no way to carry that annotation onto its
 * parameter.
 */
@Component
class KafkaDeliveryPublisher implements DeliveryPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String mainTopic;

    KafkaDeliveryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${webhook.kafka.main-topic}") String mainTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.mainTopic = mainTopic;
    }

    @Override
    public void publish(Delivery delivery, int attemptNumber) {
        var message = new DeliveryAttemptMessage(delivery.getId(), delivery.getEventId(), delivery.getEndpointId(), attemptNumber);
        kafkaTemplate.send(mainTopic, delivery.getEndpointId().toString(), writeValueAsString(message));
    }

    private String writeValueAsString(DeliveryAttemptMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize delivery-attempt message", e);
        }
    }
}
