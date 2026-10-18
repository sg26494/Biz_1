package com.store.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static int clampSize(Integer size, int max) {
        if (size == null || size <= 0) {
            return 24;
        }
        return Math.min(size, max);
    }
}
