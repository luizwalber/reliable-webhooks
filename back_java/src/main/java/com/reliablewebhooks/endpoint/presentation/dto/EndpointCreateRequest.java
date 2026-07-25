package com.reliablewebhooks.endpoint.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record EndpointCreateRequest(@NotBlank(message = "url is required") String url, String description) {
}
