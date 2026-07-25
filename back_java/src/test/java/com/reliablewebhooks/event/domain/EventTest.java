package com.reliablewebhooks.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    void receiveStartsInStateReceived() {
        Event event = Event.receive("order.created", "producer-1", "key-1", Map.of("orderId", "o-1"));

        assertThat(event.getState()).isEqualTo(EventState.RECEIVED);
        assertThat(event.getEventType()).isEqualTo("order.created");
        assertThat(event.getProducerId()).isEqualTo("producer-1");
        assertThat(event.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(event.getPayload()).containsEntry("orderId", "o-1");
        assertThat(event.getId()).isNotNull();
        assertThat(event.getReceivedAt()).isNotNull();
    }

    @Test
    void markOutboxedTransitionsFromReceivedToOutboxed() {
        Event event = Event.receive("order.created", "producer-1", "key-1", Map.of());

        event.markOutboxed();

        assertThat(event.getState()).isEqualTo(EventState.OUTBOXED);
    }
}
