package com.causa.core.domain;

/**
 * Pagination and sorting request value object.
 *
 * <p>Pure value carrier — no defaults or caps live here. All defaults
 * ({@code default-page-size}, {@code default-sort-dir}) and the max page size cap
 * are configured in {@code application.yml} under {@code causa.api.pagination.*}
 * and resolved by the service layer before constructing this record.
 *
 * <p>Page index is <strong>1-based</strong> externally (matching the API contract).
 * Use {@link #panachePage()} to obtain the 0-based index required by Panache.
 *
 * @since 0.0.2
 */
public record PageRequest(
        int page,
        int size,
        String sortBy,
        String sortDir
) {

    /**
     * Creates a {@link PageRequest} with explicit sort parameters.
     *
     * @param page    1-based page index (must be ≥ 1)
     * @param size    items per page
     * @param sortBy  field name to sort by (must be whitelisted by the caller)
     * @param sortDir {@code "asc"} or {@code "desc"}
     * @return a new {@link PageRequest}
     */
    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        return new PageRequest(page, size, sortBy, sortDir);
    }

    /**
     * Returns the 0-based page index required by Panache's {@code Page.of(index, size)}.
     *
     * @return {@code page - 1}
     */
    public int panachePage() {
        return page - 1;
    }

    /**
     * Returns {@code true} if the sort direction is ascending.
     *
     * @return {@code true} for {@code "asc"} (case-insensitive), {@code false} otherwise
     */
    public boolean isAscending() {
        return "asc".equalsIgnoreCase(sortDir);
    }
}
