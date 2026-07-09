package com.causa.config;

import com.causa.common.constants.ConfigConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert Configuration Snapshot
 *
 * <p>Typed view of all Alert-related keys from the in-memory configuration cache.
 * Constructed by {@link AppConfig} on every call to {@link AppConfig#getAlertConfig()}
 * so callers always receive the current values from the DB-backed cache.
 *
 * <p>Keys mirror {@link ConfigConstants.Alert}.
 *
 * @since 0.0.1
 */
public final class AlertConfig {

    private final String filterSeverity;
    private final String cooldownMinutes;
    private final String ignoreNamespaces;
    private final String cooldownCleanupInterval;

    AlertConfig(Map<String, String> cache) {
        this.filterSeverity           = cache.get(ConfigConstants.Alert.FILTER_SEVERITY);
        this.cooldownMinutes          = cache.get(ConfigConstants.Alert.COOLDOWN_MINUTES);
        this.ignoreNamespaces         = cache.get(ConfigConstants.Alert.IGNORE_NAMESPACES);
        this.cooldownCleanupInterval  = cache.get(ConfigConstants.Alert.COOLDOWN_CLEANUP_INTERVAL);
    }

    /**
     * Minimum severity to trigger the diagnostic pipeline.
     * Defaults to {@code "critical"} if not set.
     */
    public String getFilterSeverity() {
        return filterSeverity != null ? filterSeverity : "critical";
    }

    /**
     * Cooldown period in minutes before re-processing an alert for the same pod.
     * Defaults to {@code 15} if not set.
     */
    public int getCooldownMinutes() {
        return cooldownMinutes != null ? Integer.parseInt(cooldownMinutes) : 15;
    }

    /**
     * Comma-separated list of namespaces to ignore, parsed into a {@link List}.
     * Defaults to {@code ["kube-system", "istio-system"]} if not set.
     */
    public Optional<List<String>> getIgnoreNamespaces() {
        if (ignoreNamespaces == null || ignoreNamespaces.isBlank()) {
            return Optional.of(List.of("kube-system", "istio-system"));
        }
        return Optional.of(Arrays.stream(ignoreNamespaces.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /**
     * Cooldown cache cleanup interval string — e.g. {@code "5m"}.
     * Defaults to {@code "5m"} if not set.
     */
    public String getCooldownCleanupInterval() {
        return cooldownCleanupInterval != null ? cooldownCleanupInterval : "5m";
    }
}
