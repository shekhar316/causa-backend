package com.causa.config;

import com.causa.common.constants.ConfigConstants;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application Configuration — Master In-Memory Cache
 *
 * <p>Owns the single thread-safe in-memory store for all runtime configuration values.
 * All reads across the codebase go through this class; all writes (startup load,
 * runtime update, cache refresh) populate this class first.
 *
 * <pre>
 * Flow:
 *   startup / refresh   → ConfigServiceImpl reads DB + ENV  → calls AppConfig.put() / clear()
 *   runtime update      → ConfigServiceImpl writes to DB    → calls AppConfig.put()
 *   any feature code    → injects AppConfig directly        → appConfig.getLlmConfig().getApiKey()
 *   GET /configs API    → ConfigService.get() / getAll()    → delegates to AppConfig
 * </pre>
 *
 * <p>Usage examples:
 * <pre>
 *   appConfig.getLlmConfig().getApiKey()
 *   appConfig.getAlertConfig().getCooldownMinutes()
 *   appConfig.getMcpConfig().getKubernetes().getEndpoint()
 * </pre>
 *
 * <p>{@link LLMConfig}, {@link AlertConfig}, {@link McpConfig}, {@link ClusterConfig} are typed snapshots
 * built on each call — always reflecting the current live cache values.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AppConfig {

    /**
     * Thread-safe in-memory cache: key → plaintext value.
     * Only keys that have a non-null, non-blank value are stored here.
     * {@link ConcurrentHashMap} does not permit null values.
     */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Typed config views — the primary API for feature code
    // -------------------------------------------------------------------------

    /**
     * Returns a typed snapshot of all LLM configuration keys from the live cache.
     *
     * @return current {@link LLMConfig}
     */
    public LLMConfig getLlmConfig() {
        return new LLMConfig(cache);
    }

    /**
     * Returns a typed snapshot of all Alert configuration keys from the live cache.
     *
     * @return current {@link AlertConfig}
     */
    public AlertConfig getAlertConfig() {
        return new AlertConfig(cache);
    }

    /**
     * Returns a typed snapshot of all MCP configuration keys from the live cache.
     *
     * @return current {@link McpConfig}
     */
    public McpConfig getMcpConfig() {
        return new McpConfig(cache);
    }

    /**
     * Returns a typed snapshot of all Cluster configuration keys from the live cache.
     *
     * @return current {@link ClusterConfig}
     */
    public ClusterConfig getClusterConfig() {
        return new ClusterConfig(cache);
    }

    // -------------------------------------------------------------------------
    // Cache mutation — called only by ConfigServiceImpl
    // -------------------------------------------------------------------------

    /**
     * Stores or updates a plaintext value in the cache.
     * If value is null or blank, the key is removed instead.
     *
     * @param key   a known config key
     * @param value the plaintext value
     */
    public void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            cache.put(key, value);
        } else {
            cache.remove(key);
        }
    }

    /**
     * Removes a key from the cache.
     *
     * @param key a known config key
     */
    public void remove(String key) {
        cache.remove(key);
    }

    /**
     * Clears the entire cache. Called before a full reload (refresh).
     */
    public void clear() {
        cache.clear();
    }

    // -------------------------------------------------------------------------
    // Cache read — called by ConfigServiceImpl to serve the API
    // -------------------------------------------------------------------------

    /**
     * Returns the plaintext value for a key, or empty if absent.
     *
     * @param key a known config key
     * @return the value, or {@link Optional#empty()} if not present
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Returns an unmodifiable view of the raw cache map.
     * Used by {@code ConfigServiceImpl} to build API response entries.
     *
     * @return unmodifiable map of key → plaintext value
     */
    public Map<String, String> asMap() {
        return java.util.Collections.unmodifiableMap(cache);
    }

    // -------------------------------------------------------------------------
    // Helpers — used by ConfigServiceImpl for key iteration
    // -------------------------------------------------------------------------

    /**
     * Returns the set of all known keys across all categories.
     * Delegates to {@link ConfigConstants} — single source of truth.
     */
    public static java.util.Set<String> allKnownKeys() {
        return ConfigConstants.ALL_KNOWN_KEYS;
    }
}
