package com.causa.config.initialization;

import com.causa.common.logging.CausaLogger;
import com.causa.config.initialization.models.LlmProviderYamlModel;
import com.causa.config.initialization.models.McpServerYamlModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * YAML Configuration Reader Service
 *
 * <p>Reads and parses YAML config files for LLM providers and MCP servers.
 * Supports loading from classpath resources or filesystem paths.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class YamlConfigReaderService {

    private static final CausaLogger log = CausaLogger.getLogger(YamlConfigReaderService.class);
    private final ObjectMapper yamlMapper;

    public YamlConfigReaderService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Loads LLM providers config from the specified path.
     *
     * @param path file path or classpath resource (prefix with "classpath:")
     * @return parsed LLM providers config
     * @throws RuntimeException if file cannot be read or parsed
     */
    public LlmProviderYamlModel loadLlmProviders(String path) {
        log.info("Loading LLM providers config")
            .field("path", path)
            .log();

        try {
            LlmProviderYamlModel config = loadYaml(path, LlmProviderYamlModel.class);

            int providerCount = config.getLlmProviders() != null ? config.getLlmProviders().size() : 0;
            long activeCount = config.getLlmProviders() != null
                ? config.getLlmProviders().stream().filter(p -> Boolean.TRUE.equals(p.getActive())).count()
                : 0;

            log.info("LLM providers config loaded")
                .field("provider_count", providerCount)
                .field("active_count", activeCount)
                .log();

            return config;
        } catch (Exception e) {
            log.error("Failed to load LLM providers config")
                .field("path", path)
                .field("error", e.getMessage())
                .log();
            throw new RuntimeException("Failed to load LLM providers from: " + path, e);
        }
    }

    /**
     * Loads MCP servers config from the specified path.
     *
     * @param path file path or classpath resource (prefix with "classpath:")
     * @return parsed MCP servers config
     * @throws RuntimeException if file cannot be read or parsed
     */
    public McpServerYamlModel loadMcpServers(String path) {
        log.info("Loading MCP servers config")
            .field("path", path)
            .log();

        try {
            McpServerYamlModel config = loadYaml(path, McpServerYamlModel.class);

            int serverCount = config.getMcpServers() != null ? config.getMcpServers().size() : 0;
            long activeCount = config.getMcpServers() != null
                ? config.getMcpServers().stream().filter(s -> Boolean.TRUE.equals(s.getActive())).count()
                : 0;

            log.info("MCP servers config loaded")
                .field("server_count", serverCount)
                .field("active_count", activeCount)
                .log();

            return config;
        } catch (Exception e) {
            log.error("Failed to load MCP servers config")
                .field("path", path)
                .field("error", e.getMessage())
                .log();
            throw new RuntimeException("Failed to load MCP servers from: " + path, e);
        }
    }

    /**
     * Generic YAML loader supporting both classpath and filesystem paths.
     *
     * @param path path to YAML file (prefix with "classpath:" for classpath resources)
     * @param clazz target class for deserialization
     * @return parsed YAML object
     */
    private <T> T loadYaml(String path, Class<T> clazz) throws IOException {
        if (path.startsWith("classpath:")) {
            // Load from classpath
            String resourcePath = path.substring("classpath:".length());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Classpath resource not found: " + resourcePath);
                }
                return yamlMapper.readValue(is, clazz);
            }
        } else {
            // Load from filesystem
            File file = new File(path);
            if (!file.exists()) {
                throw new IOException("File not found: " + path);
            }
            return yamlMapper.readValue(file, clazz);
        }
    }
}
