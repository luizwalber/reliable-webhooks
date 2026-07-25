package com.reliablewebhooks.delivery.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * webhook.retry.* config (docs/adr/0004-retry-policy-and-topic-topology): an
 * ordered list of retry bands (topic + delay) plus the jitter fraction.
 * @ConfigurationProperties instead of the repo's usual per-field @Value
 * (see .claude/lombok.mdc) because this is genuinely structured, nested-list
 * config that @Value can't bind.
 */
@ConfigurationProperties(prefix = "webhook.retry")
record RetryTopology(double jitter, List<Band> bands) {

    record Band(String topic, long delayMs) {
    }
}
