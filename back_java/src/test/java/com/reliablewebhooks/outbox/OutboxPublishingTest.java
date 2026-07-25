package com.reliablewebhooks.outbox;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliablewebhooks.AbstractIntegrationTest;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.event.domain.EventState;
import com.reliablewebhooks.outbox.application.PublishOutboxEntriesUseCase;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Extends the single MockMvc + real-infra seam (docs/adr/0014-docker-compose-test-seam)
 * with a real Kafka broker: ingest via the existing HTTP boundary, drive
 * the poller directly (no waiting on @Scheduled timing), then assert the
 * real Delivery rows and the real Kafka messages.
 */
class OutboxPublishingTest extends AbstractIntegrationTest {

    @Autowired
    private PublishOutboxEntriesUseCase publishOutboxEntriesUseCase;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${webhook.kafka.main-topic}")
    private String mainTopic;

    @Test
    void fansOutToEveryRegisteredEndpointAndPublishesToKafka() throws Exception {
        String endpointAId = registerEndpoint("https://example.com/outbox-a");
        String endpointBId = registerEndpoint("https://example.com/outbox-b");
        // Fan-out targets every registered Endpoint (docs/adr/0001), and this
        // persistent test database (docs/adr/0014) may already hold endpoints
        // from other test classes — so "every endpoint" is however many that
        // actually is right now, not just the two this test just registered.
        int totalRegisteredEndpoints = endpointRepository.findAll().size();
        String eventId = ingestEvent();

        int processed = publishOutboxEntriesUseCase.pollOnce();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        assertThat(eventRepository.findById(UUID.fromString(eventId)).orElseThrow().getState())
                .isEqualTo(EventState.PUBLISHED);

        var deliveries = deliveryRepository.findByEventId(UUID.fromString(eventId), PageRequest.of(0, Math.max(totalRegisteredEndpoints, 10)))
                .getContent();
        assertThat(deliveries).hasSize(totalRegisteredEndpoints);
        assertThat(deliveries).allSatisfy(delivery -> {
            assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
            assertThat(delivery.getAttemptCount()).isZero();
        });
        assertThat(deliveries).extracting(d -> d.getEndpointId().toString())
                .contains(endpointAId, endpointBId);

        mockMvc.perform(get("/events/{id}/deliveries", eventId).param("size", String.valueOf(totalRegisteredEndpoints)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(totalRegisteredEndpoints))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));

        List<ConsumerRecord<String, String>> messagesForThisEvent = readMessagesContaining(eventId);
        assertThat(messagesForThisEvent).hasSize(totalRegisteredEndpoints);
        assertThat(messagesForThisEvent).extracting(ConsumerRecord::key).contains(endpointAId, endpointBId);
        assertThat(messagesForThisEvent).allSatisfy(record -> {
            assertThat(record.value()).contains("\"attemptNumber\":1");
            assertThat(record.value()).contains(eventId);
        });
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

    private String ingestEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", "producer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"order.created","payload":{"orderId":"o-outbox"}}"""))
                .andExpect(status().isAccepted())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private List<ConsumerRecord<String, String>> readMessagesContaining(String eventId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(mainTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            List<ConsumerRecord<String, String>> matching = new ArrayList<>();
            records.forEach(record -> {
                if (record.value() != null && record.value().contains(eventId)) {
                    matching.add(record);
                }
            });
            return matching;
        }
    }
}
