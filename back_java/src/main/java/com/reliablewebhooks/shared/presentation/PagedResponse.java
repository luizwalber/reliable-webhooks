package com.reliablewebhooks.shared.presentation;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(List<T> content, PageMetaResponse page) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(page.getContent().stream().map(mapper).toList(), PageMetaResponse.from(page));
    }
}
