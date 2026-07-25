package com.reliablewebhooks.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** See docs/adr/0004-retry-policy-and-topic-topology: attempt 1 is the main topic (out of scope here); attempt 2+ is band 0+. */
class RetryLadderTest {

    private static final RetryLadder.Band BAND_0 = new RetryLadder.Band("webhook.delivery.retry.30s", 10_000);
    private static final RetryLadder.Band BAND_1 = new RetryLadder.Band("webhook.delivery.retry.5m", 30_000);
    private static final RetryLadder.Band BAND_2 = new RetryLadder.Band("webhook.delivery.retry.30m", 120_000);

    private final RetryLadder ladder = new RetryLadder(List.of(BAND_0, BAND_1, BAND_2), 0.2);

    @Test
    void attemptOneHasNoNextBand() {
        assertThat(ladder.hasNextBand(1)).isFalse();
    }

    @Test
    void attemptTwoThroughFourMapToBandsZeroThroughTwo() {
        assertThat(ladder.hasNextBand(2)).isTrue();
        assertThat(ladder.nextAttempt(2).topic()).isEqualTo(BAND_0.topic());

        assertThat(ladder.hasNextBand(3)).isTrue();
        assertThat(ladder.nextAttempt(3).topic()).isEqualTo(BAND_1.topic());

        assertThat(ladder.hasNextBand(4)).isTrue();
        assertThat(ladder.nextAttempt(4).topic()).isEqualTo(BAND_2.topic());
    }

    @Test
    void attemptFiveExceedsTheConfiguredBands() {
        assertThat(ladder.hasNextBand(5)).isFalse();
    }

    @Test
    void nextAttemptAppliesJitterWithinTheConfiguredFraction() {
        OffsetDateTime before = OffsetDateTime.now();

        OffsetDateTime nextAttemptAt = ladder.nextAttempt(2).nextAttemptAt();

        long millisUntilDue = java.time.Duration.between(before, nextAttemptAt).toMillis();
        assertThat(millisUntilDue).isBetween((long) (BAND_0.delayMs() * 0.8) - 50, (long) (BAND_0.delayMs() * 1.2) + 50);
    }
}
