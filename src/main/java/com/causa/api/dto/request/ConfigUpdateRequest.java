package com.causa.api.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

/**
 * Config Update Request DTO
 *
 * <p>Request body for {@code POST /api/v1/configs}.
 *
 * <p>Example body:
 * <pre>{@code
 * {
 *   "configs": {
 *     "LLM_PROVIDER": "vertex-ai-anthropic",
 *     "VERTEX_LOCATION": "us-east5",
 *     "LLM_MAX_TOKENS": "8192"
 *   }
 * }
 * }</pre>
 *
 * <p>Rules:
 * <ul>
 *   <li>Every key must be declared in {@link com.causa.common.constants.ConfigConstants} (LLM, ALERT, or MCP).</li>
 *   <li>DB credential keys ({@code CAUSA_DB_*}) are not accepted — manage those via Kubernetes Secrets.</li>
 *   <li>Values must not be blank.</li>
 *   <li>Any unknown key or blank value causes the entire request to be rejected with 400.</li>
 *   <li>Sensitive keys ({@code LLM_API_KEY}, {@code VERTEX_PROJECT_ID}, {@code GOOGLE_APPLICATION_CREDENTIALS})
 *       are AES-256-GCM encrypted before storage.</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Schema(description = "Request body for upserting configuration entries.")
public record ConfigUpdateRequest(

        @Schema(
            description = "Map of config keys to their new values. Valid categories: LLM, ALERT, MCP. " +
                          "Sensitive keys are encrypted transparently. Values must not be blank.",
            example     = "{\"LLM_PROVIDER\":\"vertex-ai-anthropic\",\"VERTEX_LOCATION\":\"us-east5\"}"
        )
        Map<String, String> configs
) {}
