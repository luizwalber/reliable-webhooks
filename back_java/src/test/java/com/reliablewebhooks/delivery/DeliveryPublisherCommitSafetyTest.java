package com.reliablewebhooks.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliablewebhooks.AbstractIntegrationTest;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves DeliveryPublisher's commit-safety guarantee directly: a send made
 * inside a transaction that rolls back must never reach Kafka, and one
 * inside a transaction that commits must. See spec issue #25 — this is
 * the property KafkaDeliveryPublisher.sendAfterCommit exists to provide,
 * verified here rather than just asserted in a comment.
 */
class DeliveryPublisherCommitSafetyTest extends AbstractIntegrationTest {

    @Autowired
    private DeliveryPublisher deliveryPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${webhook.kafka.main-topic}")
    private String mainTopic;

    @Test
    void aRolledBackTransactionNeverPublishesToKafka() throws Exception {
        String endpointId = registerEndpoint("https://example.com/commit-safety-rollback");
        String eventId = ingestEvent("o-commit-safety-rollback");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            Delivery delivery = Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId));
            deliveryPublisher.publish(delivery, 1);
            throw new RollbackMarkerException();
        })).isInstanceOf(RollbackMarkerException.class);

        List<ConsumerRecord<String, String>> messages = readMessagesContaining(mainTopic, eventId, Duration.ofSeconds(5));
        assertThat(messages).isEmpty();
    }

    @Test
    void aCommittedTransactionPublishesToKafkaAfterCommit() throws Exception {
        String endpointId = registerEndpoint("https://example.com/commit-safety-commit");
        String eventId = ingestEvent("o-commit-safety-commit");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status -> {
            Delivery delivery = Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId));
            deliveryPublisher.publish(delivery, 1);
        });

        List<ConsumerRecord<String, String>> messages = readMessagesContaining(mainTopic, eventId, Duration.ofSeconds(10));
        assertThat(messages).hasSize(1);
    }

    private String registerEndpoint(String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"%s"}""".formatted(url)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String ingestEvent(String orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", "producer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"order.created","payload":{"orderId":"%s"}}""".formatted(orderId)))
                .andExpect(status().isAccepted())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private List<ConsumerRecord<String, String>> readMessagesContaining(String topic, String needle, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = org.springframework.kafka.test.utils.KafkaTestUtils.getRecords(consumer, timeout);
            List<ConsumerRecord<String, String>> matching = new java.util.ArrayList<>();
            records.forEach(record -> {
                if (record.value() != null && record.value().contains(needle)) {
                    matching.add(record);
                }
            });
            return matching;
        }
    }

    /** A distinguishable, non-Spring exception to force a rollback without tripping DataAccessException handling. */
    private static class RollbackMarkerException extends RuntimeException {
    }
}
