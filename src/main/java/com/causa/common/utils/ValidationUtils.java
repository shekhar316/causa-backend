package com.causa.common.utils;

/**
 * Validation Utilities
 *
 * <p>Lightweight helpers for validating raw string values before they are
 * stored in the configuration DB. Prevents {@link NumberFormatException} at
 * read-time by rejecting malformed numeric values at the API layer.
 *
 * @since 0.0.1
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Returns {@code true} when {@code value} can be parsed by {@link Integer#parseInt(String)}.
     *
     * @param value the raw string to test; must not be {@code null}
     * @return {@code true} if the value is a valid integer
     */
    public static boolean isValidInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns {@code true} when {@code value} can be parsed by {@link Double#parseDouble(String)}.
     *
     * @param value the raw string to test; must not be {@code null}
     * @return {@code true} if the value is a valid double
     */
    public static boolean isValidDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
