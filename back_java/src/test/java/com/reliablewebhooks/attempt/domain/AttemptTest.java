package com.reliablewebhooks.attempt.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** See docs/adr/0005-state-machine: outcome/finishedAt null is itself the in-flight signal. */
class AttemptTest {

    @Test
    void startIsInFlightWithNullOutcomeAndFinishedAt() {
        Attempt attempt = Attempt.start(UUID.randomUUID(), 1, "webhook.delivery.main");

        assertThat(attempt.getOutcome()).isNull();
        assertThat(attempt.getHttpStatusCode()).isNull();
        assertThat(attempt.getFinishedAt()).isNull();
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getTopic()).isEqualTo("webhook.delivery.main");
    }

    @Test
    void resolveSetsOutcomeStatusCodeAndFinishedAt() {
        Attempt attempt = Attempt.start(UUID.randomUUID(), 1, "webhook.delivery.main");

        attempt.resolve(AttemptOutcome.SUCCESS, 200);

        assertThat(attempt.getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(attempt.getHttpStatusCode()).isEqualTo(200);
        assertThat(attempt.getFinishedAt()).isNotNull();
    }

    @Test
    void resolveWithoutAnHttpStatusCodeCoversSyntheticOutcomes() {
        Attempt attempt = Attempt.start(UUID.randomUUID(), 2, "webhook.delivery.retry.30s");

        attempt.resolve(AttemptOutcome.CIRCUIT_OPEN, null);

        assertThat(attempt.getOutcome()).isEqualTo(AttemptOutcome.CIRCUIT_OPEN);
        assertThat(attempt.getHttpStatusCode()).isNull();
        assertThat(attempt.getFinishedAt()).isNotNull();
    }
}
