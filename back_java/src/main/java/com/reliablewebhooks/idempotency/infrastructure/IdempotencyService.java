package com.reliablewebhooks.idempotency.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.event.application.IdempotencyGateway;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed adapter for event.application.IdempotencyGateway
 * (docs/adr/0002-idempotency-and-delivery-guarantees, boundary 1). No
 * domain rules of its own — infrastructure only.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService implements IdempotencyGateway {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CachedReplay> find(String producerId, String idempotencyKey) {
        String cached = redisTemplate.opsForValue().get(key(producerId, idempotencyKey));
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, CachedReplay.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency cache entry", e);
        }
    }

    @Override
    public void store(String producerId, String idempotencyKey, CachedReplay replay) {
        try {
            String serialized = objectMapper.writeValueAsString(replay);
            redisTemplate.opsForValue().set(key(producerId, idempotencyKey), serialized, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency cache entry", e);
        }
    }

    private String key(String producerId, String idempotencyKey) {
        return "idempotency:ingest:%s:%s".formatted(producerId, idempotencyKey);
    }
}
