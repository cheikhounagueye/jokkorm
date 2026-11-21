package com.jokkorm.domain.common;

/**
 * Pagination request type — domain-native pour ne pas traîner
 * {@code org.springframework.data.domain.Pageable} dans {@code domain/}.
 *
 * <p>Semantics : {@code page} 0-based, {@code size} entre 1 et {@link #MAX_PAGE_SIZE}.
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got " + page);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "size must be in [1, %d], got %d".formatted(MAX_PAGE_SIZE, size));
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest firstPage() {
        return new PageRequest(0, DEFAULT_PAGE_SIZE);
    }
}
