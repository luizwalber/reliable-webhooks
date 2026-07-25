package com.reliablewebhooks.event.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.event.application.IdempotencyGateway;
import com.reliablewebhooks.event.application.IdempotencyGateway.CachedReplay;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Owns the idempotent-write response sequencing: replay a cached response
 * byte-for-byte on a duplicate Idempotency-Key, otherwise compute once,
 * serialize once, cache, and return the same bytes
 * (docs/adr/0002-idempotency-and-delivery-guarantees). This is an HTTP
 * delivery-mechanism concern, so it lives in presentation, not inside a
 * use case — but it's its own module so the sequencing has one place to
 * be read, changed, and tested, instead of living inline in a controller
 * method.
 */
@Component
@RequiredArgsConstructor
class IdempotentReplay {

    private final IdempotencyGateway idempotencyGateway;
    private final ObjectMapper objectMapper;

    <T> ResponseEntity<String> respond(String producerId, String idempotencyKey, HttpStatus successStatus, Supplier<T> compute) {
        var cached = idempotencyGateway.find(producerId, idempotencyKey);
        if (cached.isPresent()) {
            return toResponseEntity(cached.get());
        }

        T result = compute.get();
        CachedReplay replay = new CachedReplay(successStatus.value(), writeValueAsString(result));
        idempotencyGateway.store(producerId, idempotencyKey, replay);

        return toResponseEntity(replay);
    }

    private ResponseEntity<String> toResponseEntity(CachedReplay replay) {
        return ResponseEntity.status(replay.status()).contentType(MediaType.APPLICATION_JSON).body(replay.body());
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response body", e);
        }
    }
}
