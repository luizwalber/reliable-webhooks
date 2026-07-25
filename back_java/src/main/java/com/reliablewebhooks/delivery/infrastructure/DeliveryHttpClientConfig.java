package com.reliablewebhooks.delivery.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The RestClient used for outbound webhook delivery HTTP calls, with a bounded timeout (docs/adr/0007-hmac-signing). */
@Configuration
class DeliveryHttpClientConfig {

    @Bean
    RestClient deliveryRestClient(@Value("${webhook.delivery.http-timeout-ms}") int timeoutMs) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
