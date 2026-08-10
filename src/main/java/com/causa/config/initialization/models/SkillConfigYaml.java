package com.causa.config.initialization.models;

import java.util.Map;

/**
 * Skill Configuration YAML Model
 *
 * <p>Nested model for skill configuration in MCP servers YAML.
 *
 * @since 0.0.1
 */
public class SkillConfigYaml {

    private String name;
    private String sourceType;
    private String uri;
    private String content;
    private Boolean active;
    private Map<String, Object> metadata;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
