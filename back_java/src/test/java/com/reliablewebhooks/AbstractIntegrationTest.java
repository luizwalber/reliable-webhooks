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
 * The live delivery-worker Kafka consumer is off by default here — every
 * test class fans out into the same persistent, ever-accumulating test
 * database (docs/adr/0014), so a live consumer would race real HTTP calls
 * against every endpoint any other test class has ever registered.
 * DeliveryWorkerTest opts back in with its own @TestPropertySource,
 * which gives it an isolated Spring context (see spec issue #20).
 *
 * The outbox poller's @Scheduled auto-trigger is off for the same reason —
 * fan-out targets every registered Endpoint (docs/adr/0001), so a live
 * poller ticking in the background would fan any test's event out to
 * every other test's endpoints too, inflating Delivery counts non-
 * deterministically. Every test that needs the poller calls
 * PublishOutboxEntriesUseCase.pollOnce() directly instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"webhook.delivery-worker.enabled=false", "webhook.outbox.scheduler-enabled=false"})
public abstract class AbstractIntegrationTest {

    protected static final String OPENAPI_SPEC_PATH = "../openapi.yaml";

    @Autowired
    protected MockMvc mockMvc;
}
