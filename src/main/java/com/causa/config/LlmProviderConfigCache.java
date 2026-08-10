package com.causa.config;

import com.causa.infrastructure.persistence.entity.LlmConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Provider Configuration Cache
 *
 * <p>In-memory cache for LLM provider configurations, backed by a {@link ConcurrentHashMap}.
 * Values are loaded from the database at startup and synchronized across all pods via
 * PostgreSQL LISTEN/NOTIFY on the {@code entity_cache_channel}.
 *
 * <p>Ensures only one LLM provider is active at a time.
 *
 * <p>Thread-safe for concurrent reads and writes.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LlmProviderConfigCache {

    private final ConcurrentHashMap<String, LlmConfigurationEntity> cache = new ConcurrentHashMap<>();
    private volatile String activeLlmId;

    public Optional<LlmConfigurationEntity> get(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public Optional<LlmConfigurationEntity> getByName(String name) {
        return cache.values().stream()
            .filter(llm -> llm.getName().equals(name))
            .findFirst();
    }

    public Optional<LlmConfigurationEntity> getActive() {
        return activeLlmId != null ? get(activeLlmId) : Optional.empty();
    }

    public List<LlmConfigurationEntity> getAll() {
        return List.copyOf(cache.values());
    }

    public void put(String id, LlmConfigurationEntity entity) {
        if (entity == null) {
            cache.remove(id);
            if (id.equals(activeLlmId)) {
                activeLlmId = null;
            }
        } else {
            cache.put(id, entity);
            if (entity.getIsActive()) {
                activeLlmId = id;
            }
        }
    }

    public void remove(String id) {
        cache.remove(id);
        if (id.equals(activeLlmId)) {
            activeLlmId = null;
        }
    }

    public void clear() {
        cache.clear();
        activeLlmId = null;
    }

    public int size() {
        return cache.size();
    }

    public Map<String, LlmConfigurationEntity> asMap() {
        return Map.copyOf(cache);
    }
}
