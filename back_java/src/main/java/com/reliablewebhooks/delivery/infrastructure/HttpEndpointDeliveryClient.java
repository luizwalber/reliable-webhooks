package com.reliablewebhooks.delivery.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.attempt.domain.AttemptOutcome;
import com.reliablewebhooks.delivery.domain.DeliveryAttemptResult;
import com.reliablewebhooks.delivery.domain.EndpointDeliveryClient;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Makes the real, HMAC-signed outbound HTTP POST to an Endpoint
 * (docs/adr/0007-hmac-signing) and maps the result onto an AttemptOutcome —
 * no special-casing HTTP_4XX vs HTTP_5XX vs TIMEOUT beyond the outcome
 * label itself (docs/adr/0004-retry-policy-and-topic-topology treats them
 * uniformly).
 */
@Component
@RequiredArgsConstructor
class HttpEndpointDeliveryClient implements EndpointDeliveryClient {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String DELIVERY_ID_HEADER = "X-Webhook-Delivery-Id";

    private final RestClient deliveryRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public DeliveryAttemptResult deliver(Endpoint endpoint, Map<String, Object> eventPayload, UUID deliveryId) {
        String rawBody = writeValueAsString(eventPayload);
        String signature = HmacSigner.header(endpoint.getSecret(), rawBody, Instant.now());
        try {
            var response = deliveryRestClient.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, signature)
                    .header(DELIVERY_ID_HEADER, deliveryId.toString())
                    .body(rawBody)
                    .retrieve()
                    .toBodilessEntity();
            return new DeliveryAttemptResult(AttemptOutcome.SUCCESS, response.getStatusCode().value());
        } catch (HttpServerErrorException e) {
            return new DeliveryAttemptResult(AttemptOutcome.HTTP_5XX, e.getStatusCode().value());
        } catch (HttpClientErrorException e) {
            return new DeliveryAttemptResult(AttemptOutcome.HTTP_4XX, e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            return new DeliveryAttemptResult(AttemptOutcome.TIMEOUT, null);
        }
    }

    private String writeValueAsString(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
