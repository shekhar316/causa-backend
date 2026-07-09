package com.causa.config;

import com.causa.common.constants.ConfigConstants;

import java.util.Map;
import java.util.Optional;

/**
 * Cluster Configuration Snapshot
 *
 * <p>Typed view of all Cluster-related keys from the in-memory configuration cache.
 * Constructed by {@link AppConfig} on every call to {@link AppConfig#getClusterConfig()}
 * so callers always receive the current values from the DB-backed cache.
 *
 * <p>Keys mirror {@link ConfigConstants.Cluster}.
 *
 * @since 0.0.1
 */
public final class ClusterConfig {

    private final String clusterName;

    ClusterConfig(Map<String, String> cache) {
        this.clusterName = cache.get(ConfigConstants.Cluster.CLUSTER_NAME);
    }

    /** Human-readable name of the Kubernetes cluster being monitored. */
    public Optional<String> getClusterName() {
        return Optional.ofNullable(clusterName);
    }
}
