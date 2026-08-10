package com.causa.config.initialization;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.IdUtils;
import com.causa.config.AuthConfigCache;
import com.causa.config.LlmProviderConfigCache;
import com.causa.config.McpServerConfigCache;
import com.causa.config.SkillConfigCache;
import com.causa.config.initialization.models.AuthConfigYaml;
import com.causa.config.initialization.models.LlmProviderYamlModel;
import com.causa.config.initialization.models.McpServerYamlModel;
import com.causa.config.initialization.models.SkillConfigYaml;
import com.causa.core.ports.AuthConfigurationRepository;
import com.causa.core.ports.LlmConfigurationRepository;
import com.causa.core.ports.McpConfigurationRepository;
import com.causa.core.ports.SkillConfigurationRepository;
import com.causa.infrastructure.persistence.entity.AuthConfigurationEntity;
import com.causa.infrastructure.persistence.entity.LlmConfigurationEntity;
import com.causa.infrastructure.persistence.entity.McpConfigurationEntity;
import com.causa.infrastructure.persistence.entity.SkillConfigurationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Initialization Service
 *
 * <p>Bootstraps entity configurations (LLM providers, MCP servers, auth, skills) at application startup.
 * Loads from YAML files and seeds the database if empty. Runs at priority
 * {@link AppConstants.StartupConstants#CONFIG_PRIORITY} + 1 (21), after config startup but before cache listener.
 *
 * <p>Idempotent: only seeds if database tables are empty.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigurationInitializationService {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigurationInitializationService.class);

    @ConfigProperty(name = "causa.config.llm-config-path", defaultValue = "classpath:config/llm-providers.yaml")
    String llmConfigPath;

    @ConfigProperty(name = "causa.config.mcp-config-path", defaultValue = "classpath:config/mcp-servers.yaml")
    String mcpConfigPath;

    private final YamlConfigReaderService yamlReader;
    private final AuthConfigurationRepository authRepository;
    private final LlmConfigurationRepository llmRepository;
    private final McpConfigurationRepository mcpRepository;
    private final SkillConfigurationRepository skillRepository;
    private final AuthConfigCache authConfigCache;
    private final LlmProviderConfigCache llmProviderConfigCache;
    private final McpServerConfigCache mcpServerConfigCache;
    private final SkillConfigCache skillConfigCache;
    private final ObjectMapper objectMapper;

    // Track auth names to IDs for linking
    private final Map<String, String> authNameToId = new HashMap<>();

    @Inject
    public ConfigurationInitializationService(
        YamlConfigReaderService yamlReader,
        AuthConfigurationRepository authRepository,
        LlmConfigurationRepository llmRepository,
        McpConfigurationRepository mcpRepository,
        SkillConfigurationRepository skillRepository,
        AuthConfigCache authConfigCache,
        LlmProviderConfigCache llmProviderConfigCache,
        McpServerConfigCache mcpServerConfigCache,
        SkillConfigCache skillConfigCache,
        ObjectMapper objectMapper
    ) {
        this.yamlReader = yamlReader;
        this.authRepository = authRepository;
        this.llmRepository = llmRepository;
        this.mcpRepository = mcpRepository;
        this.skillRepository = skillRepository;
        this.authConfigCache = authConfigCache;
        this.llmProviderConfigCache = llmProviderConfigCache;
        this.mcpServerConfigCache = mcpServerConfigCache;
        this.skillConfigCache = skillConfigCache;
        this.objectMapper = objectMapper;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.CONFIG_PRIORITY + 1) StartupEvent event) {
        log.info("Entity startup: loading configurations from YAML files")
            .field("llm_config_path", llmConfigPath)
            .field("mcp_config_path", mcpConfigPath)
            .log();

        try {
            // Check if already seeded
            if (!authRepository.findAll().isEmpty() ||
                !llmRepository.findAll().isEmpty() ||
                !mcpRepository.findAll().isEmpty()) {
                log.info("Entity startup: database already seeded, loading into caches").log();
                refreshCaches();
                return;
            }

            // Load YAML configurations
            LlmProviderYamlModel llmConfig = yamlReader.loadLlmProviders(llmConfigPath);
            McpServerYamlModel mcpConfig = yamlReader.loadMcpServers(mcpConfigPath);

            // Seed database
            seedEntities(llmConfig, mcpConfig);

            // Load into caches
            refreshCaches();

            log.info("Entity startup: completed successfully").log();

        } catch (Exception e) {
            log.error("Entity startup: failed")
                .field("error", e.getClass().getSimpleName())
                .field("message", e.getMessage())
                .log();
            throw new RuntimeException("Failed to load entity configurations", e);
        }
    }

    @Transactional
    void seedEntities(LlmProviderYamlModel llmConfig, McpServerYamlModel mcpConfig) {
        log.info("Seeding entity configurations").log();

        // 1. Seed auth configurations (must be first, as LLM and MCP reference them)
        seedAuthFromLlm(llmConfig);
        seedAuthFromMcp(mcpConfig);

        // 2. Seed LLM providers
        seedLlmProviders(llmConfig);

        // 3. Seed MCP servers and skills
        seedMcpServers(mcpConfig);

        log.info("Entity configurations seeded")
            .field("auth_count", authNameToId.size())
            .field("llm_count", llmConfig.getLlmProviders().size())
            .field("mcp_count", mcpConfig.getMcpServers().size())
            .log();
    }

    private void seedAuthFromLlm(LlmProviderYamlModel config) {
        if (config.getLlmProviders() == null) return;

        for (var provider : config.getLlmProviders()) {
            if (provider.getAuth() != null) {
                // Generate a unique auth name based on provider name
                String authName = provider.getName() + "-auth";
                createAuthIfNotExists(authName, provider.getAuth());
            }
        }
    }

    private void seedAuthFromMcp(McpServerYamlModel config) {
        if (config.getMcpServers() == null) return;

        for (var server : config.getMcpServers()) {
            if (server.getAuth() != null) {
                String authName = server.getName() + "-auth";
                createAuthIfNotExists(authName, server.getAuth());
            }
        }
    }

    private void createAuthIfNotExists(String name, AuthConfigYaml authYaml) {
        if (authNameToId.containsKey(name)) {
            return; // Already created
        }

        String authId = IdUtils.generateAuthId();
        AuthConfigurationEntity entity = new AuthConfigurationEntity();
        entity.setId(authId);
        entity.setName(name);
        entity.setType(authYaml.getType());
        entity.setConfig(objectMapper.valueToTree(authYaml.getConfig()));

        authRepository.save(entity);
        authNameToId.put(name, authId);

        log.debug("Created auth configuration")
            .field("auth_id", authId)
            .field("auth_name", name)
            .field("auth_type", authYaml.getType())
            .log();
    }

    private void seedLlmProviders(LlmProviderYamlModel config) {
        if (config.getLlmProviders() == null) return;

        int activeCount = 0;

        for (var provider : config.getLlmProviders()) {
            String llmId = IdUtils.generateLlmProviderId();
            LlmConfigurationEntity entity = new LlmConfigurationEntity();
            entity.setId(llmId);
            entity.setName(provider.getName());
            entity.setProvider(provider.getProvider());
            entity.setModel(provider.getModel());
            entity.setIsActive(Boolean.TRUE.equals(provider.getActive()));
            entity.setSettings(objectMapper.valueToTree(provider.getSettings()));

            // Link to auth if present
            if (provider.getAuth() != null) {
                String authName = provider.getName() + "-auth";
                entity.setAuthId(authNameToId.get(authName));
            }

            llmRepository.save(entity);

            if (entity.getIsActive()) {
                activeCount++;
            }

            log.debug("Created LLM provider")
                .field("llm_id", llmId)
                .field("name", provider.getName())
                .field("provider", provider.getProvider())
                .field("active", entity.getIsActive())
                .log();
        }

        // Validate only one active
        if (activeCount > 1) {
            log.warn("Multiple active LLM providers found")
                .field("active_count", activeCount)
                .log();
        }
    }

    private void seedMcpServers(McpServerYamlModel config) {
        if (config.getMcpServers() == null) return;

        for (var server : config.getMcpServers()) {
            String mcpId = IdUtils.generateMcpServerId();
            McpConfigurationEntity entity = new McpConfigurationEntity();
            entity.setId(mcpId);
            entity.setName(server.getName());
            entity.setUrl(server.getUrl());
            entity.setHealthUrl(server.getHealthUrl());
            entity.setIsActive(server.getActive() != null ? server.getActive() : true);
            entity.setSettings(objectMapper.valueToTree(server.getSettings()));
            entity.setTools(objectMapper.valueToTree(server.getTools()));

            // Link to auth if present
            if (server.getAuth() != null) {
                String authName = server.getName() + "-auth";
                entity.setAuthId(authNameToId.get(authName));
            }

            mcpRepository.save(entity);

            log.debug("Created MCP server")
                .field("mcp_id", mcpId)
                .field("name", server.getName())
                .field("url", server.getUrl())
                .field("active", entity.getIsActive())
                .log();

            // Seed skills for this MCP server
            if (server.getSkills() != null) {
                seedSkills(mcpId, server.getSkills());
            }
        }
    }

    private void seedSkills(String mcpId, java.util.List<SkillConfigYaml> skills) {
        for (var skill : skills) {
            String skillId = IdUtils.generateSkillId();
            SkillConfigurationEntity entity = new SkillConfigurationEntity();
            entity.setId(skillId);
            entity.setMcpConfigurationId(mcpId);
            entity.setName(skill.getName());
            entity.setSourceType(skill.getSourceType());
            entity.setUri(skill.getUri());
            entity.setContent(skill.getContent());
            entity.setIsActive(skill.getActive() != null ? skill.getActive() : true);

            if (skill.getMetadata() != null) {
                entity.setMetadata(objectMapper.valueToTree(skill.getMetadata()));
            }

            skillRepository.save(entity);

            log.debug("Created skill")
                .field("skill_id", skillId)
                .field("name", skill.getName())
                .field("source_type", skill.getSourceType())
                .log();
        }
    }

    private void refreshCaches() {
        // Load Auth cache
        authConfigCache.clear();
        authRepository.findAll().forEach(entity -> authConfigCache.put(entity.getId(), entity));

        // Load LLM Provider cache
        llmProviderConfigCache.clear();
        llmRepository.findAll().forEach(entity -> llmProviderConfigCache.put(entity.getId(), entity));

        // Load MCP Server cache
        mcpServerConfigCache.clear();
        mcpRepository.findAll().forEach(entity -> mcpServerConfigCache.put(entity.getId(), entity));

        // Load Skill cache
        skillConfigCache.clear();
        skillRepository.findAll().forEach(entity -> skillConfigCache.put(entity.getId(), entity));

        log.info("Entity caches loaded")
            .field("auth_count", authConfigCache.size())
            .field("llm_count", llmProviderConfigCache.size())
            .field("mcp_count", mcpServerConfigCache.size())
            .field("skill_count", skillConfigCache.size())
            .log();
    }
}
