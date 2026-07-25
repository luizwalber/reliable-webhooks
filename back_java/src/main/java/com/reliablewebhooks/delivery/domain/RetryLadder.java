package com.reliablewebhooks.delivery.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which retry-band topic a given attempt number retries onto, whether the
 * attempt budget is exhausted, and the jittered delay before that retry is
 * due (docs/adr/0004-retry-policy-and-topic-topology). Pure domain — no
 * Spring, no Kafka. Attempt 1 is always the main topic (out of this
 * ladder's scope); attempt 2 is band 0, attempt 3 is band 1, etc.
 */
public class RetryLadder {

    private final List<Band> bands;
    private final double jitter;

    public RetryLadder(List<Band> bands, double jitter) {
        this.bands = bands;
        this.jitter = jitter;
    }

    public boolean hasNextBand(int attemptNumber) {
        int index = bandIndexFor(attemptNumber);
        return index >= 0 && index < bands.size();
    }

    /** Only valid when hasNextBand(attemptNumber) is true. */
    public NextAttempt nextAttempt(int attemptNumber) {
        Band band = bands.get(bandIndexFor(attemptNumber));
        long jitterMillis = (long) (band.delayMs() * jitter * ThreadLocalRandom.current().nextDouble(-1, 1));
        OffsetDateTime nextAttemptAt = OffsetDateTime.now().plus(Duration.ofMillis(band.delayMs() + jitterMillis));
        return new NextAttempt(band.topic(), nextAttemptAt);
    }

    private int bandIndexFor(int attemptNumber) {
        return attemptNumber - 2;
    }

    public record Band(String topic, long delayMs) {
    }

    public record NextAttempt(String topic, OffsetDateTime nextAttemptAt) {
    }
}
