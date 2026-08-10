package com.causa.config;

import com.causa.infrastructure.persistence.entity.AuthConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth Configuration Cache
 *
 * <p>In-memory cache for authentication configurations, backed by a {@link ConcurrentHashMap}.
 * Values are loaded from the database at startup and synchronized across all pods via
 * PostgreSQL LISTEN/NOTIFY on the {@code entity_cache_channel}.
 *
 * <p>Thread-safe for concurrent reads and writes.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AuthConfigCache {

    private final ConcurrentHashMap<String, AuthConfigurationEntity> cache = new ConcurrentHashMap<>();

    public Optional<AuthConfigurationEntity> get(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public Optional<AuthConfigurationEntity> getByName(String name) {
        return cache.values().stream()
            .filter(auth -> auth.getName().equals(name))
            .findFirst();
    }

    public List<AuthConfigurationEntity> getAll() {
        return List.copyOf(cache.values());
    }

    public void put(String id, AuthConfigurationEntity entity) {
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

    public Map<String, AuthConfigurationEntity> asMap() {
        return Map.copyOf(cache);
    }
}
