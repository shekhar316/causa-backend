package com.causa.config.initialization.models;

import java.util.List;
import java.util.Map;

/**
 * MCP Servers YAML Configuration Model
 *
 * <p>Root model for {@code mcp-servers.yaml} deserialization.
 *
 * @since 0.0.1
 */
public class McpServerYamlModel {

    private List<McpServer> mcpServers;

    public List<McpServer> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<McpServer> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public static class McpServer {
        private String name;
        private String url;
        private String healthUrl;
        private Boolean active;
        private AuthConfigYaml auth;
        private Map<String, Object> settings;
        private List<String> tools;
        private List<SkillConfigYaml> skills;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getHealthUrl() { return healthUrl; }
        public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }

        public AuthConfigYaml getAuth() { return auth; }
        public void setAuth(AuthConfigYaml auth) { this.auth = auth; }

        public Map<String, Object> getSettings() { return settings; }
        public void setSettings(Map<String, Object> settings) { this.settings = settings; }

        public List<String> getTools() { return tools; }
        public void setTools(List<String> tools) { this.tools = tools; }

        public List<SkillConfigYaml> getSkills() { return skills; }
        public void setSkills(List<SkillConfigYaml> skills) { this.skills = skills; }
    }
}
