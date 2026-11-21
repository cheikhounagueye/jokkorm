package com.jokkorm.domain.common;

import java.util.List;
import java.util.Objects;

/**
 * Page de résultats — domain-native. Le controller mappe vers un DTO REST.
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        Objects.requireNonNull(content, "content");
        content = List.copyOf(content);
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if (totalElements < 0) throw new IllegalArgumentException("totalElements must be >= 0");
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    public boolean isLast() {
        return (long) (page + 1) * size >= totalElements;
    }

    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(List.of(), request.page(), request.size(), 0L);
    }
}
