package com.causa.config.initialization.models;

import java.util.List;
import java.util.Map;

/**
 * LLM Providers YAML Configuration Model
 *
 * <p>Root model for {@code llm-providers.yaml} deserialization.
 *
 * @since 0.0.1
 */
public class LlmProviderYamlModel {

    private List<LlmProvider> llmProviders;

    public List<LlmProvider> getLlmProviders() {
        return llmProviders;
    }

    public void setLlmProviders(List<LlmProvider> llmProviders) {
        this.llmProviders = llmProviders;
    }

    public static class LlmProvider {
        private String name;
        private String provider;
        private String model;
        private Boolean active;
        private AuthConfigYaml auth;
        private Map<String, Object> settings;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }

        public AuthConfigYaml getAuth() { return auth; }
        public void setAuth(AuthConfigYaml auth) { this.auth = auth; }

        public Map<String, Object> getSettings() { return settings; }
        public void setSettings(Map<String, Object> settings) { this.settings = settings; }
    }
}
