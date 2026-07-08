package com.causa.infrastructure.persistence.repositories;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.utils.IdGenerator;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.infrastructure.persistence.entity.ConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Repository Implementation
 *
 * <p>Panache-based implementation of the {@link ConfigurationRepository} port.
 * Maps {@link ConfigurationEntity} rows to the port's lightweight
 * {@link ConfigEntry} projection.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigurationRepositoryImpl implements ConfigurationRepository {

    // -------------------------------------------------------------------------
    // Port implementation
    // -------------------------------------------------------------------------

    @Override
    public Optional<String> findByKey(String key) {
        return ConfigurationEntity.<ConfigurationEntity>find("configKey", key)
                .firstResultOptional()
                .map(ConfigurationEntity::getConfigValue);
    }

    @Override
    public List<ConfigEntry> findByCategory(String category) {
        // The configurations table is small; load all and filter in-memory
        // to avoid coupling SQL queries to the key-naming convention.
        return findAll().stream()
                .filter(e -> category != null && category.equals(ConfigConstants.categoryOf(e.key())))
                .toList();
    }

    @Override
    public List<ConfigEntry> findAll() {
        return ConfigurationEntity.<ConfigurationEntity>listAll()
                .stream()
                .map(e -> new ConfigEntry(e.getConfigKey(), e.getConfigValue(), Boolean.TRUE.equals(e.isEncrypted())))
                .toList();
    }

    @Override
    @Transactional
    public void upsert(String key, String value) {
        upsert(key, value, false);
    }

    @Override
    @Transactional
    public void upsert(String key, String value, boolean encrypted) {
        Optional<ConfigurationEntity> existing =
                ConfigurationEntity.<ConfigurationEntity>find("configKey", key)
                        .firstResultOptional();

        if (existing.isPresent()) {
            ConfigurationEntity entity = existing.get();
            entity.setConfigValue(value);
            entity.setEncrypted(encrypted);
            entity.persist();
        } else {
            ConfigurationEntity entity = new ConfigurationEntity();
            entity.setId(IdGenerator.configurationId());
            entity.setConfigKey(key);
            entity.setConfigValue(value);
            entity.setEncrypted(encrypted);
            entity.persist();
        }
    }
}
