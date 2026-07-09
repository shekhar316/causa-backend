package com.causa.core.services;

import com.causa.core.ports.ConfigurationRepository.ConfigEntry;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Service
 *
 * <p>Core port for reading and updating application configuration at runtime.
 * Configs are loaded from the {@code configurations} table on startup; missing
 * keys fall back to ENV variables and are persisted to the DB so they are
 * available on the next boot without ENV vars.
 *
 * @since 0.0.1
 */
public interface ConfigService {

    /**
     * Returns the current in-memory value for the given key.
     *
     * @param key a known config key (see {@link com.causa.common.constants.ConfigConstants})
     * @return the value, or {@link Optional#empty()} if the key is null/unknown in the cache
     * @throws IllegalArgumentException if the key is not a known config key
     */
    Optional<String> get(String key);

    /**
     * Returns all config entries for the given category.
     *
     * @param category one of {@code LLM}, {@code ALERT}, {@code MCP}, {@code DB}
     * @return list of entries for that category; never {@code null}
     */
    List<ConfigEntry> getByCategory(String category);

    /**
     * Returns all config entries across all categories.
     *
     * @return every cached entry; never {@code null}
     */
    List<ConfigEntry> getAll();

    /**
     * Updates the in-memory cache and persists the new value to the DB.
     *
     * @param key   a known config key
     * @param value the new value
     * @throws IllegalArgumentException if the key is not a known config key
     */
    void update(String key, String value);

    /**
     * Bootstraps the in-memory cache from the DB, then fills in missing keys
     * from ENV variables and writes them back to the DB.
     *
     * <p>Called once during startup by {@code ConfigStartup} after the DB pool
     * is confirmed live.
     */
    void loadFromDbAndEnv();

    /**
     * Clears the entire in-memory cache and reloads it from the DB,
     * applying yml/ENV fallback for any keys absent from the DB.
     *
     * <p>Triggered at runtime via {@code POST /api/v1/configs?refresh-cache=true}.
     * Useful after direct DB edits (migrations, admin scripts) that bypass the API.
     *
     * @return a {@link CacheRefreshResult} summarising what was loaded
     */
    CacheRefreshResult refreshCache();

    /**
     * Summary returned by {@link #refreshCache()}.
     *
     * @param keysFromDb  number of keys loaded from the DB
     * @param keysFromEnv number of keys resolved from yml/ENV (absent from DB)
     * @param keysNull    number of keys with no value in any source
     */
    record CacheRefreshResult(int keysFromDb, int keysFromEnv, int keysNull) {}
}
