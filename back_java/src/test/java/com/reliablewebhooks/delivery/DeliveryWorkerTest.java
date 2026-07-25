package com.reliablewebhooks.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.reliablewebhooks.AbstractIntegrationTest;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Drives the real @KafkaListener delivery worker end to end: register an
 * Endpoint pointing at a WireMock stub, ingest an Event, run the outbox
 * poller to get a real message onto webhook.delivery.main, then let the
 * real consumer process it — asserting the real signed HTTP call, real
 * Postgres state, and (failure case) the real republish onto the correct
 * retry-band topic. See spec issue #20 ("Testing Decisions").
 *
 * webhook.delivery-worker.enabled=true re-enables the listener that
 * AbstractIntegrationTest turns off by default, in an isolated Spring
 * context. Fan-out still targets every Endpoint accumulated by other test
 * classes in the shared persistent test database (docs/adr/0001,
 * docs/adr/0014) — this test looks up its own Delivery by endpointId
 * rather than assuming it is the only one for the Event. http-timeout-ms
 * is cut down so fanning out to accumulated junk endpoints (long-dead
 * localhost ports, or real domains) can't add much per attempt, and the
 * retry-band delays are cut from production's real 10s/30s/2min down to
 * milliseconds — the worker actually sleeps the consumer thread until
 * nextAttemptAt (docs/adr/0004), so leaving production delays in place
 * here would make every retried accumulated delivery cost real wall-clock
 * seconds to minutes.
 */
@TestPropertySource(properties = {
        "webhook.delivery-worker.enabled=true",
        "webhook.delivery.http-timeout-ms=200",
        "webhook.retry.bands[0].topic=webhook.delivery.retry.30s",
        "webhook.retry.bands[0].delay-ms=200",
        "webhook.retry.bands[1].topic=webhook.delivery.retry.5m",
        "webhook.retry.bands[1].delay-ms=300",
        "webhook.retry.bands[2].topic=webhook.delivery.retry.30m",
        "webhook.retry.bands[2].delay-ms=400"})
class DeliveryWorkerTest extends AbstractIntegrationTest {

    @Autowired
    private PublishOutboxEntriesUseCase publishOutboxEntriesUseCase;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private WireMockServer wireMockServer;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void successfulDeliveryIsSignedAndMarksDelivered() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200)));
        String endpointId = registerEndpoint("http://localhost:" + wireMockServer.port() + "/webhook");
        String eventId = ingestEvent();

        publishOutboxEntriesUseCase.pollOnce();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var delivery = deliveryFor(eventId, endpointId);
            assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERED);
        });

        wireMockServer.verify(postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-Webhook-Signature", matching("t=\\d+,v1=sha256=[0-9a-f]{64}"))
                .withHeader("X-Webhook-Delivery-Id", matching(".*"))
                .withRequestBody(containing("o-worker-success")));

        assertThat(endpointRepository.findById(UUID.fromString(endpointId)).orElseThrow().getSuccessCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void failedDeliveryReschedulesAndRepublishesToTheFirstRetryBand() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(500)));
        String endpointId = registerEndpoint("http://localhost:" + wireMockServer.port() + "/webhook");
        String eventId = ingestEvent();

        publishOutboxEntriesUseCase.pollOnce();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var delivery = deliveryFor(eventId, endpointId);
            assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
            assertThat(delivery.getAttemptCount()).isEqualTo(1);
            assertThat(delivery.getNextAttemptAt()).isNotNull();
        });

        List<ConsumerRecord<String, String>> retryMessages = readMessagesContaining("webhook.delivery.retry.30s", eventId);
        assertThat(retryMessages).anySatisfy(record -> {
            assertThat(record.key()).isEqualTo(endpointId);
            assertThat(record.value()).contains("\"attemptNumber\":2");
            assertThat(record.headers().lastHeader("nextAttemptAt")).isNotNull();
        });
    }

    /** Fan-out targets every accumulated Endpoint (docs/adr/0001, docs/adr/0014) — find this test's own Delivery among them by endpointId. */
    private Delivery deliveryFor(String eventId, String endpointId) {
        var page = deliveryRepository.findByEventId(UUID.fromString(eventId), PageRequest.of(0, 1000));
        return page.getContent().stream()
                .filter(delivery -> delivery.getEndpointId().equals(UUID.fromString(endpointId)))
                .findFirst()
                .orElseThrow();
    }

    private String registerEndpoint(String url) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"%s"}""".formatted(url)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String ingestEvent() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", "producer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"order.created","payload":{"orderId":"o-worker-success"}}"""))
                .andExpect(status().isAccepted())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private List<ConsumerRecord<String, String>> readMessagesContaining(String topic, String needle) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            List<ConsumerRecord<String, String>> matching = new ArrayList<>();
            records.forEach(record -> {
                if (record.value() != null && record.value().contains(needle)) {
                    matching.add(record);
                }
            });
            return matching;
        }
    }
}
