package com.causa.config;

import com.causa.infrastructure.persistence.entity.SkillConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill Configuration Cache
 *
 * <p>In-memory cache for skill configurations, backed by a {@link ConcurrentHashMap}.
 * Values are loaded from the database at startup and synchronized across all pods via
 * PostgreSQL LISTEN/NOTIFY on the {@code entity_cache_channel}.
 *
 * <p>Thread-safe for concurrent reads and writes.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SkillConfigCache {

    private final ConcurrentHashMap<String, SkillConfigurationEntity> cache = new ConcurrentHashMap<>();

    public Optional<SkillConfigurationEntity> get(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public List<SkillConfigurationEntity> getByMcpId(String mcpConfigurationId) {
        return cache.values().stream()
            .filter(skill -> skill.getMcpConfigurationId().equals(mcpConfigurationId))
            .toList();
    }

    public List<SkillConfigurationEntity> getActiveByMcpId(String mcpConfigurationId) {
        return cache.values().stream()
            .filter(skill -> skill.getMcpConfigurationId().equals(mcpConfigurationId))
            .filter(SkillConfigurationEntity::getIsActive)
            .toList();
    }

    public List<SkillConfigurationEntity> getAll() {
        return List.copyOf(cache.values());
    }

    public void put(String id, SkillConfigurationEntity entity) {
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

    public Map<String, SkillConfigurationEntity> asMap() {
        return Map.copyOf(cache);
    }
}
