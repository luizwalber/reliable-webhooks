package com.reliablewebhooks.event.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.event.application.IdempotencyGateway;
import com.reliablewebhooks.event.application.IdempotencyGateway.CachedReplay;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Plain-Java unit test against a fake IdempotencyGateway — no Spring context, no Redis. */
class IdempotentReplayTest {

    private final Map<String, CachedReplay> store = new HashMap<>();
    private final IdempotentReplay replay = new IdempotentReplay(new FakeIdempotencyGateway(), new ObjectMapper());

    @Test
    void computesAndCachesOnAMiss() {
        AtomicInteger computeCalls = new AtomicInteger();

        ResponseEntity<String> response = replay.respond("producer-1", "key-1", HttpStatus.ACCEPTED, () -> {
            computeCalls.incrementAndGet();
            return new TestPayload("hello");
        });

        assertThat(computeCalls.get()).isEqualTo(1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo("{\"value\":\"hello\"}");
        assertThat(store).containsKey("producer-1:key-1");
    }

    @Test
    void replaysTheCachedResponseWithoutRecomputingOnAHit() {
        replay.respond("producer-1", "key-1", HttpStatus.ACCEPTED, () -> new TestPayload("first"));

        AtomicInteger secondCallComputeCalls = new AtomicInteger();
        ResponseEntity<String> second = replay.respond("producer-1", "key-1", HttpStatus.ACCEPTED, () -> {
            secondCallComputeCalls.incrementAndGet();
            return new TestPayload("second");
        });

        assertThat(secondCallComputeCalls.get()).isZero();
        assertThat(second.getBody()).isEqualTo("{\"value\":\"first\"}");
    }

    @Test
    void scopesTheCacheKeyByBothProducerAndIdempotencyKey() {
        replay.respond("producer-a", "key-1", HttpStatus.ACCEPTED, () -> new TestPayload("a"));
        replay.respond("producer-b", "key-1", HttpStatus.ACCEPTED, () -> new TestPayload("b"));

        assertThat(store).hasSize(2);
    }

    private record TestPayload(String value) {
    }

    private class FakeIdempotencyGateway implements IdempotencyGateway {
        @Override
        public Optional<CachedReplay> find(String producerId, String idempotencyKey) {
            return Optional.ofNullable(store.get(producerId + ":" + idempotencyKey));
        }

        @Override
        public void store(String producerId, String idempotencyKey, CachedReplay replay) {
            store.put(producerId + ":" + idempotencyKey, replay);
        }
    }
}
