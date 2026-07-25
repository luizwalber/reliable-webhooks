package com.reliablewebhooks.delivery.domain;

/**
 * Domain port for putting a Delivery-attempt message onto the delivery
 * topic (docs/adr/0004-retry-policy-and-topic-topology). Implemented by
 * delivery.infrastructure.KafkaDeliveryPublisher.
 */
public interface DeliveryPublisher {

    void publish(Delivery delivery, int attemptNumber);
}
