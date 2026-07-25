package com.reliablewebhooks.event;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliablewebhooks.AbstractIntegrationTest;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.event.domain.EventState;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class EventIngestionTest extends AbstractIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void ingestsAnEventAndWritesItsOutboxRowInTheSameTransaction() throws Exception {
        String producerId = "producer-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Producer-Id", producerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"order.created","payload":{"orderId":"o-1"}}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.state").value("OUTBOXED"))
                .andExpect(jsonPath("$.producerId").value(producerId))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.payload.orderId").value("o-1"))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH))
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        UUID eventId = UUID.fromString(id);

        assertThat(eventRepository.findById(eventId)).isPresent();
        assertThat(eventRepository.findById(eventId).orElseThrow().getState()).isEqualTo(EventState.OUTBOXED);
        assertThat(outboxRepository.findAll())
                .anySatisfy(entry -> assertThat(entry.getEventId()).isEqualTo(eventId));
    }

    @Test
    void replaysTheExactOriginalResponseForADuplicateIdempotencyKeyFromTheSameProducer() throws Exception {
        String producerId = "producer-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"eventType":"order.created","payload":{"orderId":"o-2"}}""";

        MvcResult first = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Producer-Id", producerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Producer-Id", producerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH))
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void aDuplicateKeyFromADifferentProducerIsNotTreatedAsAReplay() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"eventType":"order.created","payload":{"orderId":"o-3"}}""";

        mockMvc.perform(post("/events")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Producer-Id", "producer-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));

        mockMvc.perform(post("/events")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Producer-Id", "producer-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));

        assertThat(eventRepository.findAll()).hasSize(2);
    }

    @Test
    void rejectsAnIngestRequestMissingTheRequiredPayloadField() throws Exception {
        mockMvc.perform(post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", "producer-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void getByIdReturnsNotFoundForAnUnknownEvent() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void getByIdReturnsTheEventForAKnownEvent() throws Exception {
        String producerId = "producer-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", producerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"order.created","payload":{"orderId":"o-known"}}"""))
                .andExpect(status().isAccepted())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.state").value("OUTBOXED"))
                .andExpect(jsonPath("$.producerId").value(producerId))
                .andExpect(jsonPath("$.payload.orderId").value("o-known"))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void listsIngestedEventsWithOffsetPagination() throws Exception {
        String producerId = "producer-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/events")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .header("X-Producer-Id", producerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"eventType":"order.created","payload":{"n":%d}}""".formatted(i)))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(get("/events").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void listsDeliveriesForAnEventAsAnEmptyPageInThisSlice() throws Exception {
        MvcResult result = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Producer-Id", "producer-deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"a":1}}"""))
                .andExpect(status().isAccepted())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/events/{id}/deliveries", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }
}
