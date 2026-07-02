package com.causa.core.services.impl;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.Fields;
import com.causa.common.constants.McpConstants.LogFields;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.validation.RcaValidator;
import com.causa.mcp.McpContextCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.causa.core.domain.DiagnosticContext;

import java.time.Instant;
import java.util.Optional;

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

    private final DiagnosticRepository diagnosticRepository;
    private final McpContextCollector mcpContextCollector;
    private final Optional<RcaValidator> rcaValidator;

    @Inject
    public DiagnosticServiceImpl(
        DiagnosticRepository diagnosticRepository,
        McpContextCollector mcpContextCollector,
        Optional<RcaValidator> rcaValidator
    ) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.rcaValidator = rcaValidator;
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
        // For now, just call placeholder methods synchronously
        DiagnosticContext diagnosticContext = collectContext(alert);

        // Log the complete collected context for visibility
        log.info(LogMessages.Diagnostic.CONTEXT_COLLECTED)
            .field(Fields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field(LogFields.HAS_K8S_CONTEXT, diagnosticContext.hasKubernetesContext())
            .field(LogFields.HAS_KRUIZE_CONTEXT, diagnosticContext.hasKruizeContext())
            .field(LogFields.HAS_CRYOSTAT_CONTEXT, diagnosticContext.hasCryostatContext())
            .log();

        String contextForLLM = diagnosticContext.toString();
        String separator = ContextConstants.SEPARATOR_CHAR.repeat(ContextConstants.SEPARATOR_LENGTH);
        
        // Log the full formatted context that will be sent to LLM
        log.info(ContextConstants.NEWLINE + separator + ContextConstants.NEWLINE +
                 ContextConstants.CONTEXT_LOG_HEADER + ContextConstants.NEWLINE +
                 separator + ContextConstants.NEWLINE +
                 contextForLLM +
                 separator + ContextConstants.NEWLINE)
            .field(Fields.DIAGNOSTIC_ID, diagnosticId)
            .log();

        determineDiagnosisType(alert);
        performRootCauseAnalysis(alert);
        validateRca(alert);

        return diagnostic;
    }

    /**

     * Collects diagnostic context from all MCP servers (Kubernetes, Cryostat, Kruize).
     *
     * <p>Aggregates pod status, events, logs, resource recommendations, and JFR analysis
     * from multiple MCP servers into a single {@link com.causa.core.domain.DiagnosticContext} object.
     *
     * @param alert the alert to collect context for
     * @return diagnostic context with all collected data
     */
    private DiagnosticContext collectContext(Alert alert) {
        log.debug(LogMessages.Diagnostic.CONTEXT_COLLECTION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        return mcpContextCollector.collectContext(alert);
    }

    /**
     * Placeholder: Determines the type of diagnosis needed based on alert characteristics.
     *
     * <p>Future implementation will classify the alert (OOM, GC thrashing, memory leak, etc.)
     * and route to appropriate diagnostic templates.
     *
     * @param alert the alert to classify
     */
    private void determineDiagnosisType(Alert alert) {
        log.debug(LogMessages.Diagnostic.DIAGNOSIS_TYPE_DETERMINED)
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Implement diagnosis type classification
        // - Analyze alert name and labels
        // - Route to appropriate prompt template
    }

    /**
     * Placeholder: Performs root cause analysis using LLM.
     *
     * <p>Future implementation will use LangChain4J to:
     * <ul>
     *   <li>Build structured prompts with context</li>
     *   <li>Call LLM (Claude/Ollama/Bob)</li>
     *   <li>Parse structured JSON response</li>
     *   <li>Extract fault domain and confidence score</li>
     * </ul>
     *
     * @param alert the alert to analyze
     */
    private void performRootCauseAnalysis(Alert alert) {
        log.debug(LogMessages.Diagnostic.ROOT_CAUSE_ANALYSIS_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Implement LLM-based RCA
        // - Use LangChain4J prompt templates
        // - Call LLM with structured output
        // - Parse and validate response
    }

    /**
     * Validates RCA output against diagnostic context.
     *
     * <p>Uses assertion-driven validation to verify each claim in the RCA
     * against the collected diagnostic context. Validates:
     * <ul>
     *   <li>Observations and facts against K8s events and metrics</li>
     *   <li>Trends against time-series data</li>
     *   <li>Causal relationships against evidence chains</li>
     *   <li>Configuration claims against actual settings</li>
     * </ul>
     *
     * @param alert the alert being analyzed
     * @param rca the root cause analysis to validate
     * @param diagnosticContext the collected MCP context
     * @return validated RCA with assertion-level validation results
     */
    private ValidatedRCA validateRca(Alert alert, RootCauseAnalysis rca, String diagnosticContext) {
        log.info(LogMessages.Diagnostic.RCA_VALIDATION_STARTED)
            .field("alertId", alert.getAlertId())
            .field("issueTitle", rca.issueTitle())
            .log();

        // Check if validator is available
        if (rcaValidator.isEmpty()) {
            log.warn("RCA validator not available, skipping validation")
                .field("alertId", alert.getAlertId())
                .log();

            // Return unvalidated RCA wrapped in ValidatedRCA with no validation results
            return ValidatedRCA.builder()
                .originalRca(rca)
                .validationResults(java.util.List.of())
                .validatedAt(Instant.now())
                .build();
        }

        // Perform validation
        ValidatedRCA validatedRCA = rcaValidator.get().validate(rca, diagnosticContext);

        // Log validation results
        logValidationResults(validatedRCA);

        // Log validation summary
        log.info("RCA validation completed")
            .field("alertId", alert.getAlertId())
            .field("validationSummary", validatedRCA.summary().toSummaryString())
            .field("isValid", validatedRCA.isValid())
            .field("isHighConfidence", validatedRCA.isHighConfidence())
            .field("supportedAssertions", validatedRCA.getSupportedAssertions().size())
            .field("unsupportedAssertions", validatedRCA.getUnsupportedAssertions().size())
            .field("unknownAssertions", validatedRCA.getUnknownAssertions().size())
            .log();

        return validatedRCA;
    }

    /**
     * Logs detailed validation results for each assertion.
     */
    private void logValidationResults(ValidatedRCA validatedRCA) {
        log.info("\n" + "=".repeat(80))
            .log();
        log.info("RCA VALIDATION RESULTS")
            .log();
        log.info("=".repeat(80))
            .log();

        // Log each assertion's validation result
        for (ValidationResult result : validatedRCA.validationResults()) {
            String statusSymbol = switch (result.status()) {
                case SUPPORTED -> "✓";
                case PARTIALLY_SUPPORTED -> "~";
                case UNSUPPORTED -> "✗";
                case UNKNOWN -> "?";
            };

            log.info(String.format("[%s] %s", statusSymbol, result.assertion().text()))
                .field("assertionId", result.assertion().id())
                .field("type", result.assertion().type())
                .field("source", result.assertion().source())
                .field("status", result.status())
                .field("confidence", String.format("%.2f", result.confidence()))
                .field("supportingEvidence", result.supportingEvidence().size())
                .field("refutingEvidence", result.refutingEvidence().size())
                .log();

            // Log evidence details if present
            if (!result.supportingEvidence().isEmpty()) {
                for (int i = 0; i < result.supportingEvidence().size(); i++) {
                    var evidence = result.supportingEvidence().get(i);
                    log.debug(String.format("  Evidence %d: %s (relevance: %.2f)",
                        i + 1,
                        evidence.snippet().substring(0, Math.min(100, evidence.snippet().length())),
                        evidence.relevanceScore()))
                        .field("evidenceSource", evidence.source())
                        .field("evidenceType", evidence.type())
                        .log();
                }
            }

            // Log explanation
            result.explanation().ifPresent(explanation ->
                log.debug("  Explanation: " + explanation)
                    .log()
            );
        }

        log.info("=".repeat(80))
            .log();
        log.info("VALIDATION SUMMARY")
            .log();
        log.info(validatedRCA.summary().toSummaryString())
            .log();
        log.info("=".repeat(80))
            .log();
    }
}
