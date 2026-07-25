package com.reliablewebhooks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The one test seam for this implementation slice: MockMvc driving the real
 * REST controllers against real Postgres and Redis — no mocks for either.
 * See spec issue #17 ("Testing Decisions").
 *
 * Postgres and Redis are NOT managed by this class — bring them up with
 * `docker compose -f docker-compose.test.yml up -d` before running these
 * tests (see docs/adr/0014-docker-compose-test-seam.md for why this
 * replaced Testcontainers-managed containers).
 *
 * Every live background process (a @KafkaListener, a @Scheduled poller,
 * any future one) is gated behind a webhook.background.<process-name>.enabled
 * property, default true in production — see spec issue #27. All of them
 * are forced false here: every test class fans out into the same
 * persistent, ever-accumulating test database (docs/adr/0014), and
 * fan-out targets every registered Endpoint (docs/adr/0001), so a live
 * background process would race real HTTP calls, or inflate Delivery
 * counts, against every endpoint/event any OTHER test class has ever
 * registered/ingested — not just its own.
 *
 * Currently gated this way:
 * - webhook.background.delivery-worker.enabled (DeliveryAttemptConsumer's
 *   @KafkaListener) — DeliveryWorkerTest opts back in with its own
 *   @TestPropertySource, which gives it an isolated Spring context.
 * - webhook.background.outbox-poller.enabled (PublishOutboxEntriesUseCase's
 *   @Scheduled trigger) — every test that needs the poller calls
 *   pollOnce() directly instead, same as the Kafka-consumer tests do.
 *
 * Adding a new background process? Follow the same convention: a
 * webhook.background.<name>.enabled property (default true), forced
 * false in the @TestPropertySource below, with a one-line addition to
 * the list above — don't invent a new naming shape.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "webhook.background.delivery-worker.enabled=false",
        "webhook.background.outbox-poller.enabled=false"})
public abstract class AbstractIntegrationTest {

    protected static final String OPENAPI_SPEC_PATH = "../openapi.yaml";

    @Autowired
    protected MockMvc mockMvc;
}
