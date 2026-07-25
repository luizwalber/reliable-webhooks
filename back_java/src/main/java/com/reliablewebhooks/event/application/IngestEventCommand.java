package com.reliablewebhooks.event.application;

import java.util.Map;

public record IngestEventCommand(String producerId, String idempotencyKey, String eventType, Map<String, Object> payload) {
}
