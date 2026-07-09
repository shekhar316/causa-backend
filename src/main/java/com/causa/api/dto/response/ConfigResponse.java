package com.causa.api.dto.response;

import com.causa.common.constants.ConfigConstants;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Config Response DTO
 *
 * <p>Represents a single configuration entry returned from the config API.
 *
 * @param key       the configuration key
 * @param value     the stored value; {@code ********} for sensitive keys, {@code null} if not configured
 * @param category  the key's category — one of {@code LLM}, {@code ALERT}, {@code MCP}
 * @param encrypted {@code true} when the value is stored AES-256-GCM encrypted in the DB
 * @since 0.0.1
 */
@Schema(description = "A single runtime configuration entry.")
public record ConfigResponse(
        @Schema(description = "Configuration key", example = "LLM_PROVIDER")
        String key,

        @Schema(description = "Current value; '********' for sensitive keys; null if not configured",
                example = "vertex-ai-anthropic", nullable = true)
        String value,

        @Schema(description = "Category this key belongs to", example = "LLM",
                enumeration = {"LLM", "ALERT", "MCP"})
        String category,

        @Schema(description = "True when the value is sensitive and stored encrypted in the DB; the value field shows '********'",
                example = "false")
        boolean encrypted
) {

    /**
     * Factory method for plain (non-sensitive) keys.
     */
    public static ConfigResponse of(String key, String value) {
        boolean sensitive = ConfigConstants.isSensitive(key);
        String  displayed = (sensitive && value != null) ? ConfigConstants.MASKED_VALUE : value;
        return new ConfigResponse(key, displayed, ConfigConstants.categoryOf(key), sensitive);
    }

    /**
     * Factory method used by the service layer — carries the encrypted flag from
     * the {@link com.causa.core.ports.ConfigurationRepository.ConfigEntry}.
     */
    public static ConfigResponse of(String key, String value, boolean encrypted) {
        return new ConfigResponse(key, value, ConfigConstants.categoryOf(key), encrypted);
    }
}
