package com.causa.config.initialization.models;

import java.util.Map;

/**
 * Auth Configuration YAML Model
 *
 * <p>Nested model for authentication configuration in YAML files.
 * Used by both LLM providers and MCP servers.
 *
 * @since 0.0.1
 */
public class AuthConfigYaml {

    private String type;
    private Map<String, Object> config;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
