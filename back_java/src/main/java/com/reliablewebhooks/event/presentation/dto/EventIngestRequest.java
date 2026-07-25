package com.reliablewebhooks.event.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EventIngestRequest(
        @NotNull(message = "payload is required") Map<String, Object> payload,
        String eventType) {
}
