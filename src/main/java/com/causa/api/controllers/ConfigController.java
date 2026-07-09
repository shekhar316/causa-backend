package com.causa.api.controllers;

import com.causa.api.dto.request.ConfigUpdateRequest;
import com.causa.common.constants.LLMConstants;
import com.causa.common.utils.ValidationUtils;
import com.causa.api.dto.response.ConfigUpdateResponse;
import com.causa.api.dto.response.ConfigResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.ConfigConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.ports.ConfigurationRepository.ConfigEntry;
import com.causa.core.services.ConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration Controller
 *
 * <p>REST endpoints for reading and updating application configuration at runtime.
 *
 * <pre>
 * GET  /api/v1/configs              → all keys (optionally filtered by ?category=LLM|ALERT|MCP)
 * GET  /api/v1/configs/{key}        → single key
 * POST /api/v1/configs              → upsert {"KEY":"value", ...}
 * </pre>
 *
 * <p>Only keys declared in {@link ConfigConstants} are accepted.
 * Unknown keys cause the entire POST request to be rejected with 400.
 *
 * @since 0.0.1
 */
@Tag(name = "Configuration", description = "Runtime configuration management — read and update LLM, Alert, and MCP settings stored in the database.")
@Path(ApiConstants.Paths.Configs.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigController {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigController.class);

    private final ConfigService configService;

    @Inject
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs  (optionally ?category=LLM|ALERT|MCP)
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "List all configuration entries",
        description = "Returns every known configuration key with its current value.\n\n" +
                      "- `value` is `null` when the key has not been configured yet.\n" +
                      "- Sensitive keys (`LLM_API_KEY`, `VERTEX_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`) " +
                      "always return `********` as the value and `encrypted: true`.\n\n" +
                      "**Query parameters:**\n" +
                      "- `?category=LLM|ALERT|MCP` — filter results to a single category.\n" +
                      "- `?refresh-cache=true` — clears the in-memory cache, reloads from DB (then yml/ENV fallback " +
                      "for any missing keys), then returns the refreshed values."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config entries returned",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(type = SchemaType.ARRAY, implementation = ConfigResponse.class)))
    })
    @GET
    public Response getAll(
        @Parameter(description = "Filter by category: LLM, ALERT, or MCP", example = "LLM",
                   schema = @Schema(enumeration = {"LLM", "ALERT", "MCP"}))
        @QueryParam(ApiConstants.Paths.Configs.QUERY_CATEGORY) String category,
        @Parameter(description = "When true, clears the cache and reloads from DB + yml/ENV before returning",
                   example = "true", schema = @Schema(defaultValue = "false"))
        @QueryParam(ApiConstants.Paths.Configs.QUERY_REFRESH_CACHE) @jakarta.ws.rs.DefaultValue("false") boolean refreshCache) {

        if (refreshCache) {
            configService.refreshCache();
        }

        List<ConfigEntry> entries = (category != null && !category.isBlank())
                ? configService.getByCategory(category.toUpperCase())
                : configService.getAll();

        List<ConfigResponse> body = entries.stream()
                .map(e -> ConfigResponse.of(e.key(), e.value(), e.encrypted()))
                .toList();

        return Response.ok(body).build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs/{key}
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Get a single configuration entry",
        description = "Returns the current value for a specific configuration key.\n\n" +
                      "- Returns `400` if the key is not a recognised config key.\n" +
                      "- Returns `200` with `value: null` if the key is valid but not yet configured.\n" +
                      "- Sensitive keys (`LLM_API_KEY`, `VERTEX_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`) " +
                      "return `value: ********` and `encrypted: true`."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config entry found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ConfigResponse.class))),
        @APIResponse(responseCode = "400", description = "Unknown configuration key — not declared in ConfigConstants",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    @Path("/{key}")
    public Response getByKey(
        @Parameter(description = "A valid configuration key, e.g. LLM_PROVIDER or CAUSA_ALERT_SEVERITY",
                   required = true, example = "LLM_PROVIDER")
        @PathParam("key") String key) {

        if (!ConfigConstants.isValidKey(key)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(400, LogMessages.Config.UNKNOWN_KEY, key))
                    .build();
        }

        // Sensitive keys get masked value + encrypted=true
        boolean sensitive = ConfigConstants.isSensitive(key);
        String  value     = configService.get(key).orElse(null);
        String  displayed = (sensitive && value != null) ? ConfigConstants.MASKED_VALUE : value;
        return Response.ok(ConfigResponse.of(key, displayed, sensitive)).build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/configs  — upsert
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Update configuration entries",
        description = "Accepts a JSON body `{ \"configs\": { \"KEY\": \"value\", ... } }` and upserts each entry.\n\n" +
                      "**Validation (all-or-nothing):**\n" +
                      "- Every key must be declared in `ConfigConstants` (LLM, ALERT, or MCP category).\n" +
                      "- Every value must be non-blank.\n" +
                      "- If ANY key is unknown or ANY value is blank, the **entire request** is rejected with `400`.\n\n" +
                      "**Sensitive keys** (`LLM_API_KEY`, `VERTEX_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`) " +
                      "are AES-256-GCM encrypted before being written to the database. " +
                      "The plaintext value is accepted in the request and stored encrypted transparently.\n\n" +
                      "**Note:** Database connection credentials (`CAUSA_DB_*`) are managed exclusively via " +
                      "Kubernetes Secrets and cannot be updated through this API."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "All entries persisted and cache refreshed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ConfigUpdateResponse.class))),
        @APIResponse(responseCode = "400", description = "Empty body, unknown key(s), or blank value(s)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @POST
    public Response update(
        @RequestBody(description = "Map of config keys to new values. Sensitive keys are encrypted transparently.",
            required = true,
            content = @Content(schema = @Schema(implementation = ConfigUpdateRequest.class)))
        ConfigUpdateRequest request) {

        // Guard: body must be present and non-empty
        if (request == null || request.configs() == null || request.configs().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(400, LogMessages.Config.KEY_BLANK,
                            "Request body must contain at least one config entry"))
                    .build();
        }

        Map<String, String> incoming = request.configs();

        // --- Validate ALL keys and values upfront — reject the whole request on first error ---
        List<String> invalidKeys   = new ArrayList<>();
        List<String> blankValues   = new ArrayList<>();
        List<String> invalidValues = new ArrayList<>();

        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (!ConfigConstants.isValidKey(k)) {
                invalidKeys.add(k);
            } else if (v == null || v.isBlank()) {
                blankValues.add(k);
            } else if (ConfigConstants.LLM.PROVIDER.equals(k) && !LLMConstants.Provider.ALL.contains(v)) {
                invalidValues.add(k + "=" + v + " (accepted: " + LLMConstants.Provider.ALL + ")");
            } else if (ConfigConstants.LLM.MODEL_NAME.equals(k) && !LLMConstants.ModelNames.ALL.contains(v)) {
                invalidValues.add(k + "=" + v + " (accepted: " + LLMConstants.ModelNames.ALL + ")");
            } else if (ConfigConstants.isIntegerKey(k) && !ValidationUtils.isValidInteger(v)) {
                invalidValues.add(k + "=" + v + " (must be a valid integer)");
            } else if (ConfigConstants.isDoubleKey(k) && !ValidationUtils.isValidDouble(v)) {
                invalidValues.add(k + "=" + v + " (must be a valid number)");
            }
        }

        if (!invalidKeys.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(400, LogMessages.Config.UNKNOWN_KEY,
                            "Unknown config keys: " + invalidKeys))
                    .build();
        }

        if (!blankValues.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(400, LogMessages.Config.VALUE_BLANK,
                            "Blank values provided for keys: " + blankValues))
                    .build();
        }

        if (!invalidValues.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(400, LogMessages.Config.INVALID_VALUE,
                            "Invalid values: " + invalidValues))
                    .build();
        }

        // --- All valid — persist and refresh cache ---
        List<String> updatedKeys = new ArrayList<>();
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            configService.update(entry.getKey(), entry.getValue());
            updatedKeys.add(entry.getKey());
        }

        log.info(LogMessages.Config.UPDATED)
                .field(ConfigConstants.LogFields.KEYS_LOADED, updatedKeys.size())
                .field(ConfigConstants.LogFields.CONFIG_SOURCE, ConfigConstants.LogFields.SOURCE_DB)
                .log();

        return Response.ok(new ConfigUpdateResponse(updatedKeys, List.of())).build();
    }
}
