package com.causa.core.services;

import com.causa.common.constants.PromptConstants;
import com.causa.config.RcaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt Template Loader
 *
 * <p>Loads and caches YAML-based prompt templates for different LLM models.
 * Supports model-specific prompt variations (vertex-ai-anthropic, direct-anthropic, bob, ollama)
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PromptTemplateLoader {

    private final String templatePath;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    @Inject
    public PromptTemplateLoader(RcaConfig rcaConfig) {
        String configuredPath = rcaConfig.templatePath();
        // Normalize path - ensure it has leading "/" for classloader resource lookup
        this.templatePath = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    /**
     * Loads a prompt template for the specified model type.
     *
     * @param modelType the model type (default, bob, ollama, etc.)
     * @return the prompt template
     * @throws IllegalArgumentException if template not found for model type
     */
    public PromptTemplate loadTemplate(String modelType) {
        return templateCache.computeIfAbsent(modelType, this::loadTemplateFromYaml);
    }

    /**
     * Loads template from YAML file.
     */
    @SuppressWarnings("unchecked")
    private PromptTemplate loadTemplateFromYaml(String modelType) {
        try (InputStream is = getClass().getResourceAsStream(templatePath)) {
    @SuppressWarnings("unchecked")
    private PromptTemplate loadTemplateFromYaml(String modelType) {
        if (templatePath == null || templatePath.isBlank()) {
            throw new IllegalStateException("Prompt template path must be configured and non-empty.");
        }

        String normalizedTemplatePath = templatePath.startsWith("/") ? templatePath : "/" + templatePath;

        try (InputStream is = getClass().getResourceAsStream(normalizedTemplatePath)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Prompt template file not found. Configured templatePath='" + templatePath
                                + "', resolved resource path='" + normalizedTemplatePath + "'.");
            }

            Yaml yaml = new Yaml();
            Map<String, Object> templates = yaml.load(is);

            Map<String, Object> modelTemplate = (Map<String, Object>) templates.get(modelType);
            if (modelTemplate == null) {
                // Fallback to default if model-specific template not found
                modelTemplate = (Map<String, Object>) templates.get(PromptConstants.DEFAULT_MODEL_TYPE);
                if (modelTemplate == null) {
                    throw new IllegalStateException(
                        String.format("Default prompt template (%s) not found in %s",
                            PromptConstants.DEFAULT_MODEL_TYPE, templatePath)
                    );
                }
            }

            return new PromptTemplate(
                (String) modelTemplate.get(PromptConstants.KEY_NAME),
                (String) modelTemplate.get(PromptConstants.KEY_VERSION),
                (String) modelTemplate.get(PromptConstants.KEY_DESCRIPTION),
                (String) modelTemplate.get(PromptConstants.KEY_SYSTEM_PROMPT),
                (String) modelTemplate.get(PromptConstants.KEY_USER_PROMPT)
            );

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load prompt template for model type: " + modelType, e);
        }
    }

    /**
     * Prompt Template Record
     */
    public record PromptTemplate(
        String name,
        String version,
        String description,
        String systemPrompt,
        String userPrompt
    ) {
        /**
         * Renders the user prompt by replacing placeholders with actual values.
         *
         * @param alertName     the alert name
         * @param severity      the alert severity (will be converted to string)
         * @param podName       the pod name
         * @param namespace     the namespace
         * @param containerName the container name
         * @param context       the MCP context string
         * @return the rendered prompt
         */
        public String render(String alertName, Object severity, String podName,
                             String namespace, String containerName, String context) {
            return userPrompt
                .replace(PromptConstants.PLACEHOLDER_ALERT_NAME, alertName)
                .replace(PromptConstants.PLACEHOLDER_SEVERITY, severity != null ? severity.toString() : "unknown")
                .replace(PromptConstants.PLACEHOLDER_POD_NAME, podName)
                .replace(PromptConstants.PLACEHOLDER_NAMESPACE, namespace)
                .replace(PromptConstants.PLACEHOLDER_CONTAINER_NAME, containerName != null ? containerName : "N/A")
                .replace(PromptConstants.PLACEHOLDER_CONTEXT, context);
        }
    }
}
