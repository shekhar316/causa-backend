package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.RcaPromptBuilder;
import com.causa.mcp.McpContextCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Diagnostic Service Implementation
 *
 * <p>Implements the diagnostic pipeline with placeholder methods for future LLM integration.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticServiceImpl implements DiagnosticService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticServiceImpl.class);

    // JSON parsing constants
    private static final String JSON_CODE_BLOCK_PREFIX = "```json";
    private static final String CODE_BLOCK_PREFIX = "```";
    private static final int JSON_CODE_BLOCK_PREFIX_LENGTH = 7;
    private static final int CODE_BLOCK_PREFIX_LENGTH = 3;

    private final DiagnosticRepository diagnosticRepository;
    private final McpContextCollector mcpContextCollector;
    private final RcaPromptBuilder rcaPromptBuilder;
    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.rcaPromptBuilder = rcaPromptBuilder;
        this.promptSender = promptSender;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field("alertId", alert.getAlertId())
            .field("alertName", alert.getAlertName())
            .log();

        // Generate diagnostic ID
        Instant now = Instant.now();
        String diagnosticId = Diagnostic.generateDiagnosticId(alert.getAlertId(), now);

        // Create diagnostic in PENDING status
        Diagnostic diagnostic = Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alert.getAlertId())
            .status(DiagnosticStatus.PENDING)
            .generatedAt(now)
            .build();

        // Persist diagnostic
        diagnostic = diagnosticRepository.save(diagnostic);

        log.info("Diagnostic created")
            .field("diagnosticId", diagnosticId)
            .field("alertId", alert.getAlertId())
            .field("status", DiagnosticStatus.PENDING.getValue())
            .log();

        // TODO: Trigger async diagnostic pipeline
        // For now, just call methods synchronously
        collectContext(alert); // Logs context to console (existing MCP integration)
        String contextForLLM = buildContextForLLM(alert); // Build formatted context for LLM
        RootCauseAnalysis rca = performRootCauseAnalysis(alert, contextForLLM);

        // TODO: Store RCA result in database
        // TODO: validateRca(alert, rca);

        return diagnostic;
    }

    /**
     * Builds context string to be sent to LLM.
     *
     * <p>Collects diagnostic context from MCP servers and formats as structured string.
     *
     * @param alert the alert to build context for
     * @return formatted context string for LLM
     */
    private String buildContextForLLM(Alert alert) {
        log.debug("Building LLM context")
            .field("alertId", alert.getAlertId())
            .log();

        // Collect context from MCP servers
        String contextString = mcpContextCollector.collectContextAsString(alert);

        log.debug("LLM context built")
            .field("alertId", alert.getAlertId())
            .field("contextLength", contextString.length())
            .log();

        return contextString;
    }

    /**
     * Collects context from MCP servers and logs results.
     *
     * <p>Calls Kubernetes MCP for pod status, events, and logs.
     * This method logs context to console for debugging.
     *
     * @param alert the alert to collect context for
     */
    private void collectContext(Alert alert) {
        log.debug(LogMessages.Diagnostic.CONTEXT_COLLECTION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        mcpContextCollector.collectAndLogContext(alert);
    }

    /**
     * Performs root cause analysis using LLM.
     *
     * <p>Builds the RCA prompt from YAML template, calls the LLM, and parses
     * the structured JSON response into a RootCauseAnalysis object.
     *
     * @param alert the alert to analyze
     * @param contextString the collected MCP context
     * @return the RCA result
     */
    private RootCauseAnalysis performRootCauseAnalysis(Alert alert, String contextString) {
        log.debug(LogMessages.Diagnostic.ROOT_CAUSE_ANALYSIS_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        try {
            // Build the prompt using YAML template
            String systemPrompt = rcaPromptBuilder.getSystemPrompt();
            String userPrompt = rcaPromptBuilder.buildPrompt(alert, contextString);

            log.info("RCA prompt built")
                .field("alertId", alert.getAlertId())
                .field("systemPromptLength", systemPrompt.length())
                .field("userPromptLength", userPrompt.length())
                .log();

            log.debug("Context and prompts prepared")
                .field("alertId", alert.getAlertId())
                .field("contextLength", contextString.length())
                .field("systemPromptLength", systemPrompt.length())
                .field("userPromptLength", userPrompt.length())
                .log();

            // Build LLM request
            LLMRequest llmRequest = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .temperature(0.1)  // Low temperature for deterministic RCA
                .maxTokens(4096)
                .build();

            // Call the LLM (works with both LangChain and BobShell)
            LLMResponse llmResponse = promptSender.send(llmRequest);

            log.info("LLM response received")
                .field("alertId", alert.getAlertId())
                .field("modelUsed", llmResponse.modelUsed())
                .field("inputTokens", llmResponse.inputTokens())
                .field("outputTokens", llmResponse.outputTokens())
                .field("latencyMs", llmResponse.latencyMs())
                .log();

            // Parse JSON response to RootCauseAnalysis
            String responseText = llmResponse.responseText();

            log.debug("Parsing LLM response")
                .field("alertId", alert.getAlertId())
                .field("responseLength", responseText.length())
                .log();

            RootCauseAnalysis rca = parseRcaResponse(responseText);

            log.info("RCA generated successfully")
                .field("alertId", alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
                .field("rcaConfidence", rca.llmConfidenceScoreForRca())
                .field("solutionConfidence", rca.llmConfidenceScoreForSolution())
                .log();

            return rca;

        } catch (Exception e) {
            log.error("RCA generation failed")
                .field("alertId", alert.getAlertId())
                .exception(e)
                .log();
            throw new RuntimeException("Failed to generate RCA for alert: " + alert.getAlertId(), e);
        }
    }

    /**
     * Parses the LLM JSON response into a RootCauseAnalysis object.
     *
     * @param responseText the LLM response text (should be JSON)
     * @return the parsed RCA
     */
    private RootCauseAnalysis parseRcaResponse(String responseText) throws Exception {
        // Clean the response - remove markdown code blocks if present
        String jsonText = responseText.trim();
        if (jsonText.startsWith(JSON_CODE_BLOCK_PREFIX)) {
            jsonText = jsonText.substring(JSON_CODE_BLOCK_PREFIX_LENGTH);
        } else if (jsonText.startsWith(CODE_BLOCK_PREFIX)) {
            jsonText = jsonText.substring(CODE_BLOCK_PREFIX_LENGTH);
        }
        if (jsonText.endsWith(CODE_BLOCK_PREFIX)) {
            jsonText = jsonText.substring(0, jsonText.length() - CODE_BLOCK_PREFIX_LENGTH);
        }
        jsonText = jsonText.trim();

        // Parse JSON to RootCauseAnalysis
        return objectMapper.readValue(jsonText, RootCauseAnalysis.class);
    }

    /**
     * Validates LLM output using hybrid validation engine.
     *
     * <p>Future implementation will:
     * <ul>
     *   <li>Verify LLM provided evidence citations</li>
     *   <li>Apply deterministic sanity checks against metrics</li>
     *   <li>Run critic LLM for adversarial validation</li>
     * </ul>
     *
     * @param alert the alert being analyzed
     * @param rca the RCA result to validate
     */
    private void validateRca(Alert alert, RootCauseAnalysis rca) {
        log.debug(LogMessages.Diagnostic.RCA_VALIDATION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Implement hybrid validation
        // - Evidence assertion verification
        // - Rule-based metric validation
        // - Optional critic LLM pass
    }
}
