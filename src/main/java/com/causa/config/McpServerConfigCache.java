package com.causa.config;

import com.causa.infrastructure.persistence.entity.McpConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server Configuration Cache
 *
 * <p>In-memory cache for MCP server configurations, backed by a {@link ConcurrentHashMap}.
 * Values are loaded from the database at startup and synchronized across all pods via
 * PostgreSQL LISTEN/NOTIFY on the {@code entity_cache_channel}.
 *
 * <p>Thread-safe for concurrent reads and writes.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpServerConfigCache {

    private final ConcurrentHashMap<String, McpConfigurationEntity> cache = new ConcurrentHashMap<>();

    public Optional<McpConfigurationEntity> get(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public Optional<McpConfigurationEntity> getByName(String name) {
        return cache.values().stream()
            .filter(mcp -> mcp.getName().equals(name))
            .findFirst();
    }

    public List<McpConfigurationEntity> getAll() {
        return List.copyOf(cache.values());
    }

    public List<McpConfigurationEntity> getActive() {
        return cache.values().stream()
            .filter(McpConfigurationEntity::getIsActive)
            .toList();
    }

    public void put(String id, McpConfigurationEntity entity) {
        if (entity == null) {
            cache.remove(id);
        } else {
            cache.put(id, entity);
        }
    }

    public void remove(String id) {
        cache.remove(id);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Map<String, McpConfigurationEntity> asMap() {
        return Map.copyOf(cache);
    }
}
