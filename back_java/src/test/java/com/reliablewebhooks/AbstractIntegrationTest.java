package com.reliablewebhooks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String OPENAPI_SPEC_PATH = "../openapi.yaml";

    @Autowired
    protected MockMvc mockMvc;
}
