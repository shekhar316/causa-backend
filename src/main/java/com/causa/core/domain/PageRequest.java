package com.causa.core.domain;

/**
 * Pagination and sorting request value object.
 *
 * <p>Carries the validated page/size/sort parameters from the API layer
 * through to the repository layer. All values are pre-validated before
 * construction — this object is always in a consistent state.
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

    /** Default page size returned when the client omits {@code page_size}. */
    public static final int DEFAULT_SIZE = 20;

    /** Hard upper cap on page size — enforced at the service layer. */
    public static final int MAX_SIZE = 100;

    /** Default sort direction. */
    public static final String DEFAULT_SORT_DIR = "desc";

    /**
     * Creates a {@link PageRequest} with explicit sort parameters.
     *
     * @param page    1-based page index (must be ≥ 1)
     * @param size    items per page (must be 1–100)
     * @param sortBy  field name to sort by (must be whitelisted by the caller)
     * @param sortDir {@code "asc"} or {@code "desc"}
     * @return a new {@link PageRequest}
     */
    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        return new PageRequest(page, size, sortBy, sortDir);
    }

    /**
     * Creates a {@link PageRequest} using the default sort direction.
     *
     * @param page   1-based page index
     * @param size   items per page
     * @param sortBy field name to sort by
     * @return a new {@link PageRequest}
     */
    public static PageRequest of(int page, int size, String sortBy) {
        return new PageRequest(page, size, sortBy, DEFAULT_SORT_DIR);
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
