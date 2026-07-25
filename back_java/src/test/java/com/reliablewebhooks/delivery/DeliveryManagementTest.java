package com.reliablewebhooks.delivery;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliablewebhooks.AbstractIntegrationTest;
import com.reliablewebhooks.attempt.domain.Attempt;
import com.reliablewebhooks.attempt.domain.AttemptOutcome;
import com.reliablewebhooks.attempt.domain.AttemptRepository;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Delivery/Attempt read + manual-retry API (spec issue #22), over data
 * the outbox poller and delivery worker already produce correctly
 * (issues #18, #20) — those pipelines aren't re-exercised here. Deliveries
 * and Attempts are seeded directly via the repositories; Event/Endpoint
 * still go through the real HTTP boundary so the seeded rows satisfy the
 * real foreign keys, same seam as every other integration test
 * (docs/adr/0014-docker-compose-test-seam).
 */
class DeliveryManagementTest extends AbstractIntegrationTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Test
    void listDeliveriesFiltersByEndpointId() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-a");
        String eventId = ingestEvent("o-dm-a");
        Delivery delivery = deliveryRepository.save(Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId)));

        mockMvc.perform(get("/deliveries").param("endpointId", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(delivery.getId().toString()))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void listDeliveriesFilteredByStateDeadIsTheDlqView() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-b");
        String eventId = ingestEvent("o-dm-b");
        Delivery delivery = Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId));
        delivery.startDelivering();
        delivery.markDead();
        deliveryRepository.save(delivery);

        mockMvc.perform(get("/deliveries").param("endpointId", endpointId).param("state", "DEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].state").value("DEAD"))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void getByIdReturnsTheDelivery() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-c");
        String eventId = ingestEvent("o-dm-c");
        Delivery delivery = deliveryRepository.save(Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId)));

        mockMvc.perform(get("/deliveries/{deliveryId}", delivery.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(delivery.getId().toString()))
                .andExpect(jsonPath("$.state").value("SCHEDULED"))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void getByIdReturns404ForAnUnknownDelivery() throws Exception {
        mockMvc.perform(get("/deliveries/{deliveryId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void listAttemptsReturnsTheTimelineOldestFirstIncludingAnInFlightAttempt() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-d");
        String eventId = ingestEvent("o-dm-d");
        Delivery delivery = deliveryRepository.save(Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId)));

        Attempt first = Attempt.start(delivery.getId(), 1, "webhook.delivery.main");
        first.resolve(AttemptOutcome.HTTP_5XX, 500);
        attemptRepository.save(first);
        Attempt second = Attempt.start(delivery.getId(), 2, "webhook.delivery.retry.30s");
        attemptRepository.save(second); // still in flight — outcome null

        // No openApi().isValid() here: swagger-request-validator-mockmvc validates each
        // allOf branch independently, and AttemptOutcome's ref doesn't itself declare
        // nullable — only the wrapping "nullable: true" sibling does — so a real null
        // outcome (the in-flight signal, docs/adr/0005-state-machine) fails validation
        // even though it's the schema's own documented shape. Same class of tooling
        // limitation as ADR-0012's "Exception to every test gets openApi().isValid()".
        mockMvc.perform(get("/deliveries/{deliveryId}/attempts", delivery.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.content[0].outcome").value("HTTP_5XX"))
                .andExpect(jsonPath("$.content[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.content[1].outcome").value(nullValue()));
    }

    @Test
    void listAttemptsReturns404ForAnUnknownDelivery() throws Exception {
        mockMvc.perform(get("/deliveries/{deliveryId}/attempts", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void retrySucceedsOnADeadDeliveryAndResetsItsState() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-e");
        String eventId = ingestEvent("o-dm-e");
        Delivery delivery = Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId));
        delivery.startDelivering();
        delivery.markDead();
        deliveryRepository.save(delivery);

        mockMvc.perform(post("/deliveries/{deliveryId}/retry", delivery.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));

        assertThat(deliveryRepository.findById(delivery.getId()).orElseThrow().getAttemptCount()).isZero();
    }

    @Test
    void retryReturns409WhenTheDeliveryIsNotDead() throws Exception {
        String endpointId = registerEndpoint("https://example.com/dm-f");
        String eventId = ingestEvent("o-dm-f");
        Delivery delivery = deliveryRepository.save(Delivery.schedule(UUID.fromString(eventId), UUID.fromString(endpointId)));

        mockMvc.perform(post("/deliveries/{deliveryId}/retry", delivery.getId()))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
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
}
