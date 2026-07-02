package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.validation.AssertionAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.properties.IfBuildProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LLM-based assertion analyzer.
 *
 * <p>Uses an LLM to intelligently verify assertions by:
 * <ul>
 *   <li>Asking targeted questions about the assertion</li>
 *   <li>Searching for evidence in the diagnostic context</li>
 *   <li>Evaluating the strength of evidence</li>
 *   <li>Providing confidence scores and explanations</li>
 * </ul>
 *
 * <p>Enabled when: causa.validation.assertion-analyzer=llm
 *
 * @since 0.0.1
 */
@ApplicationScoped
@IfBuildProperty(name = "causa.validation.assertion-analyzer", stringValue = "llm", enableIfMissing = true)
public class LlmAssertionAnalyzer implements AssertionAnalyzer {

    private static final CausaLogger log = CausaLogger.getLogger(LlmAssertionAnalyzer.class);

    /**
     * System prompt for assertion analysis.
     *
     * <p>Instructs the LLM to act as an expert validator that verifies claims
     * against diagnostic evidence.
     */
    private static final String ANALYSIS_SYSTEM_PROMPT = """
        You are an expert validator for Kubernetes root cause analysis.

        Your task: Verify an assertion against diagnostic context.

        Process:
        1. Understand the assertion - what claim is being made?
        2. Ask targeted questions to verify the claim:
           - For OBSERVATION: "Does the context show this fact?"
           - For TREND: "Does the context show this pattern over time?"
           - For CAUSALITY: "Does the context support this cause-effect relationship?"
           - For CONFIGURATION: "Does the context confirm this setting?"
        3. Search the diagnostic context for evidence
        4. Evaluate evidence strength and relevance
        5. Determine validation status and confidence

        Evidence Quality:
        - DIRECT: Explicit statement in context (e.g., "Reason: OOMKilled")
        - STRONG: Multiple corroborating data points
        - MODERATE: Indirect evidence or single data point
        - WEAK: Circumstantial or partial evidence
        - NONE: No supporting evidence found

        Validation Status:
        - SUPPORTED: Strong direct evidence supports the claim
        - PARTIALLY_SUPPORTED: Some evidence but not conclusive
        - UNSUPPORTED: Evidence contradicts the claim
        - UNKNOWN: Insufficient evidence to validate

        Return JSON:
        {
          "status": "SUPPORTED|PARTIALLY_SUPPORTED|UNSUPPORTED|UNKNOWN",
          "confidence": 0.0-1.0,
          "evidence": [
            {
              "snippet": "exact text from context",
              "source": "section name",
              "relevance": 0.0-1.0,
              "type": "KUBERNETES_EVENT|METRIC|POD_LOG|etc"
            }
          ],
          "reasoning": "Explain why this status and confidence",
          "questions_asked": [
            "What questions did you ask to verify?"
          ]
        }
        """;

    private final PromptSender promptSender;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;

    @Inject
    public LlmAssertionAnalyzer(
        PromptSender promptSender,
        LLMConfig llmConfig,
        ObjectMapper objectMapper
    ) {
        this.promptSender = promptSender;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public ValidationResult analyze(Assertion assertion, String diagnosticContext) {
        log.debug("Analyzing assertion with LLM")
            .field("assertionId", assertion.id())
            .field("assertionType", assertion.type())
            .field("assertionText", assertion.text())
            .log();

        // Skip validation for recommendations
        if (assertion.type() == Assertion.AssertionType.RECOMMENDATION) {
            return ValidationResult.unknown(
                assertion,
                "Recommendations are not validated against evidence"
            );
        }

        try {
            // Build analysis prompt
            String userPrompt = buildAnalysisPrompt(assertion, diagnosticContext);

            // Call LLM
            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(ANALYSIS_SYSTEM_PROMPT)
                .temperature(0.2) // Low temperature for consistent analysis
                .maxTokens(3000)  // Allow detailed analysis
                .build();

            LLMResponse response = promptSender.send(request);

            // Parse response
            AnalysisResult result = parseAnalysisResponse(response.responseText());

            // Convert to ValidationResult
            ValidationResult validationResult = toValidationResult(assertion, result);

            log.info("LLM assertion analysis completed")
                .field("assertionId", assertion.id())
                .field("status", validationResult.status())
                .field("confidence", validationResult.confidence())
                .field("evidenceCount", validationResult.evidenceCount())
                .log();

            return validationResult;

        } catch (Exception e) {
            log.error("LLM assertion analysis failed")
                .field("assertionId", assertion.id())
                .exception(e)
                .log();

            // Return unknown on error
            return ValidationResult.unknown(
                assertion,
                "Analysis failed: " + e.getMessage()
            );
        }
    }

    @Override
    public List<ValidationResult> analyzeAll(
        List<Assertion> assertions,
        String diagnosticContext
    ) {
        log.info("Analyzing all assertions with LLM")
            .field("totalAssertions", assertions.size())
            .log();

        List<ValidationResult> results = new ArrayList<>();

        for (Assertion assertion : assertions) {
            ValidationResult result = analyze(assertion, diagnosticContext);
            results.add(result);
        }

        log.info("Batch analysis completed")
            .field("totalAssertions", assertions.size())
            .field("supported", results.stream().filter(r -> r.status() == ValidationResult.ValidationStatus.SUPPORTED).count())
            .field("unsupported", results.stream().filter(r -> r.status() == ValidationResult.ValidationStatus.UNSUPPORTED).count())
            .log();

        return results;
    }

    /**
     * Builds the analysis prompt for the LLM.
     */
    private String buildAnalysisPrompt(Assertion assertion, String diagnosticContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# ASSERTION TO VERIFY\n\n");
        prompt.append("**Text:** ").append(assertion.text()).append("\n");
        prompt.append("**Type:** ").append(assertion.type()).append("\n");
        prompt.append("**Source:** ").append(assertion.source()).append("\n");

        // Add type-specific guidance
        prompt.append("\n# VERIFICATION APPROACH\n\n");
        prompt.append(getVerificationGuidance(assertion.type()));

        prompt.append("\n# DIAGNOSTIC CONTEXT\n\n");
        prompt.append("Search the following diagnostic context for evidence:\n\n");
        prompt.append("```\n");
        prompt.append(diagnosticContext);
        prompt.append("\n```\n");

        prompt.append("\n# YOUR TASK\n\n");
        prompt.append("1. Ask targeted questions about this ").append(assertion.type()).append(" assertion\n");
        prompt.append("2. Search the context for evidence\n");
        prompt.append("3. Evaluate evidence quality and relevance\n");
        prompt.append("4. Return JSON with status, confidence, evidence, and reasoning\n");

        return prompt.toString();
    }

    /**
     * Gets verification guidance based on assertion type.
     */
    private String getVerificationGuidance(Assertion.AssertionType type) {
        return switch (type) {
            case OBSERVATION -> """
                For OBSERVATION assertions, verify:
                - Is this fact directly stated in the context?
                - Are there specific events, logs, or metrics confirming this?
                - Look for: pod status, events, exit codes, error messages

                Example questions:
                - "Does the context show the container was OOMKilled?"
                - "Is there an event with Reason: OOMKilled?"
                - "What is the exit code?"
                """;

            case TREND -> """
                For TREND assertions, verify:
                - Are there multiple data points showing this pattern?
                - Does the data show increase/decrease over time?
                - Look for: time-series metrics, sequential values

                Example questions:
                - "Does memory usage show an increasing trend?"
                - "Are there metrics at different timestamps?"
                - "What are the values at T1, T2, T3?"
                """;

            case CAUSALITY -> """
                For CAUSALITY assertions, verify:
                - Is there evidence for the cause?
                - Is there evidence for the effect?
                - Is the causal link supported?
                - Look for: temporal ordering, mechanism explanation

                Example questions:
                - "Did the cause occur before the effect?"
                - "Does the context explain the mechanism?"
                - "Are there other possible causes?"
                """;

            case CONFIGURATION -> """
                For CONFIGURATION assertions, verify:
                - Is this setting explicitly stated?
                - Are the values/limits mentioned?
                - Look for: resource limits, requests, quotas

                Example questions:
                - "What is the memory limit?"
                - "What are the resource requests?"
                - "Are there any quotas configured?"
                """;

            case RECOMMENDATION -> """
                Recommendations are not validated against context.
                They are suggestions based on the RCA, not facts to verify.
                """;
        };
    }

    /**
     * Parses the LLM analysis response.
     */
    private AnalysisResult parseAnalysisResponse(String responseText) throws Exception {
        // Clean response
        String jsonText = responseText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        // Parse JSON
        return objectMapper.readValue(jsonText, AnalysisResult.class);
    }

    /**
     * Converts AnalysisResult to ValidationResult.
     */
    private ValidationResult toValidationResult(Assertion assertion, AnalysisResult result) {
        // Parse status
        ValidationResult.ValidationStatus status = ValidationResult.ValidationStatus.valueOf(
            result.status.toUpperCase()
        );

        // Convert evidence
        List<Evidence> evidenceList = new ArrayList<>();
        if (result.evidence != null) {
            for (EvidenceDto dto : result.evidence) {
                Evidence evidence = Evidence.of(
                    dto.source != null ? dto.source : "llm-analysis",
                    parseEvidenceType(dto.type),
                    dto.snippet,
                    dto.relevance
                );
                evidenceList.add(evidence);
            }
        }

        // Build explanation
        StringBuilder explanation = new StringBuilder();
        if (result.reasoning != null) {
            explanation.append(result.reasoning);
        }
        if (result.questionsAsked != null && !result.questionsAsked.isEmpty()) {
            explanation.append("\n\nQuestions asked:\n");
            for (String question : result.questionsAsked) {
                explanation.append("- ").append(question).append("\n");
            }
        }

        // Return validation result
        return new ValidationResult(
            assertion,
            status,
            result.confidence,
            status == ValidationResult.ValidationStatus.SUPPORTED ||
            status == ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED
                ? evidenceList : List.of(),
            status == ValidationResult.ValidationStatus.UNSUPPORTED
                ? evidenceList : List.of(),
            Optional.of(explanation.toString().trim())
        );
    }

    /**
     * Parses evidence type from string.
     */
    private Evidence.EvidenceType parseEvidenceType(String typeStr) {
        if (typeStr == null) {
            return Evidence.EvidenceType.OTHER;
        }
        try {
            return Evidence.EvidenceType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Evidence.EvidenceType.OTHER;
        }
    }

    /**
     * DTO for LLM analysis result.
     */
    private static class AnalysisResult {
        public String status;
        public double confidence;
        public List<EvidenceDto> evidence;
        public String reasoning;
        public List<String> questionsAsked;

        // Jackson needs default constructor
        public AnalysisResult() {}
    }

    /**
     * DTO for evidence in analysis result.
     */
    private static class EvidenceDto {
        public String snippet;
        public String source;
        public double relevance;
        public String type;

        // Jackson needs default constructor
        public EvidenceDto() {}
    }
}
