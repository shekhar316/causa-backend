package com.causa.core.ports;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Repository Port
 *
 * <p>Outbound port (hexagonal architecture) for persisting and retrieving
 * configuration key-value pairs from the {@code configurations} table.
 *
 * <p>Implementations live in the infrastructure layer; callers must never
 * depend on anything below this interface.
 *
 * @since 0.0.1
 */
public interface ConfigurationRepository {

    /**
     * Returns the stored value for the given key, or {@link Optional#empty()} if
     * the key does not exist in the table.
     *
     * @param key the configuration key (e.g. {@code LLM_PROVIDER})
     * @return the stored value wrapped in an Optional
     */
    Optional<String> findByKey(String key);

    /**
     * Returns all configuration entries whose key belongs to the given category
     * prefix pattern.
     *
     * @param category one of {@code LLM}, {@code ALERT}, {@code MCP}, {@code DB}
     * @return list of {@link ConfigEntry} for that category; never {@code null}
     */
    List<ConfigEntry> findByCategory(String category);

    /**
     * Returns every configuration entry in the table.
     *
     * @return all entries; never {@code null}
     */
    List<ConfigEntry> findAll();

    /**
     * Inserts a new row if the key does not exist, or updates the existing row's
     * value when it does (upsert semantics). Marks the row as plain-text.
     *
     * @param key   the configuration key
     * @param value the value to store
     */
    void upsert(String key, String value);

    /**
     * Inserts or updates a configuration row with explicit encryption flag.
     *
     * @param key       the configuration key
     * @param value     the value to store (already encrypted by the caller when {@code encrypted=true})
     * @param encrypted {@code true} when the value has been encrypted by the application layer
     */
    void upsert(String key, String value, boolean encrypted);

    /**
     * Lightweight projection of a single configuration row.
     *
     * @param key       the configuration key
     * @param value     the stored value; may be {@code null}
     * @param encrypted {@code true} when the value is stored in encrypted form
     */
    record ConfigEntry(String key, String value, boolean encrypted) {}
}
