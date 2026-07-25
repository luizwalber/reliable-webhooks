package com.reliablewebhooks.delivery.domain;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the real, HMAC-signed outbound HTTP call to an Endpoint
 * (docs/adr/0007-hmac-signing). Implemented by delivery.infrastructure's
 * RestClient-based adapter.
 */
public interface EndpointDeliveryClient {

    DeliveryAttemptResult deliver(Endpoint endpoint, Map<String, Object> eventPayload, UUID deliveryId);
}
