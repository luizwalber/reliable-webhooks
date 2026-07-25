package com.reliablewebhooks.endpoint.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EndpointTest {

    @Test
    void registerStartsClosedWithZeroCountsAndNullSuccessRate() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", "test", "secret-value");

        assertThat(endpoint.getCircuitBreakerState()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(endpoint.getSuccessCount()).isZero();
        assertThat(endpoint.getDeadCount()).isZero();
        assertThat(endpoint.successRate()).isNull();
        assertThat(endpoint.getSecret()).isEqualTo("secret-value");
    }

    @Test
    void successRateIsSuccessCountOverTotalTerminalDeliveries() {
        Endpoint endpoint = Endpoint.reconstitute(
                java.util.UUID.randomUUID(), "https://example.com/hook", null, "secret",
                CircuitBreakerState.CLOSED, 3, 1, java.time.OffsetDateTime.now());

        assertThat(endpoint.successRate()).isEqualTo(0.75);
    }

    @Test
    void recordSuccessIncrementsSuccessCount() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", null, "secret");

        endpoint.recordSuccess();

        assertThat(endpoint.getSuccessCount()).isEqualTo(1);
        assertThat(endpoint.getDeadCount()).isZero();
    }

    @Test
    void recordFailureIncrementsDeadCount() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", null, "secret");

        endpoint.recordFailure();

        assertThat(endpoint.getDeadCount()).isEqualTo(1);
        assertThat(endpoint.getSuccessCount()).isZero();
    }

    @Test
    void updateCircuitBreakerStateSyncsTheReadModelSnapshot() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", null, "secret");

        endpoint.updateCircuitBreakerState(CircuitBreakerState.OPEN);

        assertThat(endpoint.getCircuitBreakerState()).isEqualTo(CircuitBreakerState.OPEN);
    }
}
