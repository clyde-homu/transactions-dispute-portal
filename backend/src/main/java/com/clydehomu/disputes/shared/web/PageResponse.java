package com.clydehomu.disputes.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** The contract's page envelope: {@code items} plus the paging numbers, never Spring's own Page JSON. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
