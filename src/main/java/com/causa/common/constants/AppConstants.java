package com.causa.common.constants;

/**
 * Application Constants
 *
 * <p>Contains application-wide constants.
 *
 * @since 1.0.0
 */
public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    /**
     * Startup priority constants for CDI observer ordering.
     *
     * @since 0.0.1
     */
    public static final class StartupConstants {
        private StartupConstants() {}

        /** LLM initialization priority. */
        public static final int LLM_PRIORITY = 10;
    }
}
