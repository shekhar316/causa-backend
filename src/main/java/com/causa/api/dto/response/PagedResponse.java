package com.causa.api.dto.response;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * <p>Returned by list endpoints that support pagination.
 * Clients use {@code page} and {@code page_size} query parameters to control pagination.
 *
 * @param <T>        the item type
 * @param items      the current page of items
 * @param page       the current page index (1-based)
 * @param pageSize   the maximum number of items per page
 * @param total      the total number of matching items across all pages
 * @param totalPages the total number of pages
 *
 * @since 0.0.1
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {

    /**
     * Constructs a {@link PagedResponse} from a pre-sliced list and total count.
     *
     * @param <T>      item type
     * @param items    the sliced list for the current page
     * @param page     current page (1-based)
     * @param pageSize items per page
     * @param total    total item count across all pages
     * @return a new {@link PagedResponse}
     */
    public static <T> PagedResponse<T> of(List<T> items, int page, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PagedResponse<>(items, page, pageSize, total, totalPages);
    }
}
