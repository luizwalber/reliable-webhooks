package com.reliablewebhooks.delivery.infrastructure;

import com.reliablewebhooks.delivery.domain.DeliveryDedupChecker;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed fast-path cache for delivery-time dedup
 * (docs/adr/0002-idempotency-and-delivery-guarantees, boundary 3). TTL
 * covers the full retry ladder; a cache miss just costs one extra
 * Postgres-backed check (the already-loaded Delivery's state) in the
 * caller — this cache is never the sole source of truth.
 */
@Component
@RequiredArgsConstructor
class RedisDeliveryDedupChecker implements DeliveryDedupChecker {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String MARKER = "true";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isMarkedDelivered(UUID eventId, UUID endpointId) {
        return MARKER.equals(redisTemplate.opsForValue().get(key(eventId, endpointId)));
    }

    @Override
    public void markDelivered(UUID eventId, UUID endpointId) {
        redisTemplate.opsForValue().set(key(eventId, endpointId), MARKER, TTL);
    }

    private String key(UUID eventId, UUID endpointId) {
        return "delivery:done:%s:%s".formatted(eventId, endpointId);
    }
}
