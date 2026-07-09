package com.causa.api.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Config Update Response DTO
 *
 * <p>Returned from {@code POST /api/v1/configs} after processing a config update.
 *
 * @param updated list of keys that were successfully updated
 * @param rejected list of keys that were rejected (unknown or blank value)
 * @since 0.0.1
 */
@Schema(description = "Result of a configuration update.")
public record ConfigUpdateResponse(

        @Schema(description = "Keys that were successfully persisted and cache-refreshed")
        List<String> updated,

        @Schema(description = "Keys that were rejected — unknown key or blank value")
        List<String> rejected
) {}
