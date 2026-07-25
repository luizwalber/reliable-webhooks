package com.reliablewebhooks.shared.presentation;

import org.springframework.data.domain.Page;

public record PageMetaResponse(int page, int size, long totalElements, int totalPages) {

    public static PageMetaResponse from(Page<?> page) {
        return new PageMetaResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
