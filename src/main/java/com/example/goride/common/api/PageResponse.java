package com.example.goride.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        Pagination pagination
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(
                items,
                new Pagination(page, size, totalItems, totalPages)
        );
    }

    public record Pagination(
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {
    }
}
