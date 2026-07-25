package com.reliablewebhooks.delivery.infrastructure;

import java.util.UUID;

/** Kafka wire format for webhook.delivery.main (docs/adr/0004-retry-policy-and-topic-topology). */
record DeliveryAttemptMessage(UUID deliveryId, UUID eventId, UUID endpointId, int attemptNumber) {
}
