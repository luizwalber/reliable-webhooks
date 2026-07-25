package com.reliablewebhooks.delivery.application;

import java.util.UUID;

/** Input to ProcessDeliveryAttemptUseCase — the Kafka-shaped message plus the topic it was consumed from. */
public record DeliveryAttemptCommand(UUID deliveryId, UUID eventId, UUID endpointId, int attemptNumber, String topic) {
}
