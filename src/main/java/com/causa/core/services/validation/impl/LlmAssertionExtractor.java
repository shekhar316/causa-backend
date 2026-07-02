package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.config.LLMConfig;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.validation.AssertionExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.properties.IfBuildProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LLM-based assertion extractor.
 *
 * <p>Uses an LLM to intelligently extract atomic assertions from RCA output.
 * More sophisticated than rule-based splitting - understands context and
 * can split complex sentences into multiple assertions.
 *
 * <p>Enabled when: causa.validation.assertion-extractor=llm
 *
 * @since 0.0.1
 */
@ApplicationScoped
@IfBuildProperty(name = "causa.validation.assertion-extractor", stringValue = "llm")
public class LlmAssertionExtractor implements AssertionExtractor {

    private static final CausaLogger log = CausaLogger.getLogger(LlmAssertionExtractor.class);

    private static final String EXTRACTION_SYSTEM_PROMPT = """
        You are an expert at analyzing root cause analysis (RCA) text and extracting atomic assertions.

        Your task: Extract individual, verifiable claims from RCA text.

        Rules:
        1. Each assertion should be ONE atomic fact
        2. Split compound sentences into separate assertions
        3. Classify each assertion by type:
           - OBSERVATION: Direct fact (e.g., "Container was OOMKilled")
           - TREND: Pattern over time (e.g., "Memory usage increased")
           - CAUSALITY: Cause-effect (e.g., "OOMKill caused by heap exhaustion")
           - CONFIGURATION: Setting/config (e.g., "Memory limit set to 512Mi")
           - RECOMMENDATION: Solution (e.g., "Increase memory limit")

        Return JSON array of assertions:
        [
          {
            "text": "Container was OOMKilled",
            "type": "OBSERVATION"
          },
          {
            "text": "Heap usage continuously increased",
            "type": "TREND"
          }
        ]
        """;

    private final PromptSender promptSender;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;

    @Inject
    public LlmAssertionExtractor(
        PromptSender promptSender,
        LLMConfig llmConfig,
        ObjectMapper objectMapper
    ) {
        this.promptSender = promptSender;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Assertion> extractAssertions(RootCauseAnalysis rca) {
        log.info("Extracting assertions using LLM")
            .field("issueTitle", rca.issueTitle())
            .field("extractorType", "llm")
            .log();

        List<Assertion> allAssertions = new ArrayList<>();

        // Extract from root cause
        if (rca.rootCause() != null && !rca.rootCause().isBlank()) {
            allAssertions.addAll(extractFromText(
                rca.rootCause(),
                Assertion.AssertionSource.ROOT_CAUSE,
                "rootCause"
            ));
        }

        // Extract from issue description
        if (rca.issueDescription() != null && !rca.issueDescription().isBlank()) {
            allAssertions.addAll(extractFromText(
                rca.issueDescription(),
                Assertion.AssertionSource.ISSUE_DESCRIPTION,
                "issueDescription"
            ));
        }

        // Extract from technical description
        if (rca.technicalDescription() != null && !rca.technicalDescription().isBlank()) {
            allAssertions.addAll(extractFromText(
                rca.technicalDescription(),
                Assertion.AssertionSource.TECHNICAL_DESCRIPTION,
                "technicalDescription"
            ));
        }

        // Extract from solutions
        if (rca.possibleSolutions() != null && !rca.possibleSolutions().isEmpty()) {
            for (int i = 0; i < rca.possibleSolutions().size(); i++) {
                var solution = rca.possibleSolutions().get(i);
                allAssertions.addAll(extractFromText(
                    solution.solution(),
                    Assertion.AssertionSource.POSSIBLE_SOLUTIONS,
                    "possibleSolutions[" + i + "]"
                ));
            }
        }

        log.info("LLM assertion extraction completed")
            .field("totalAssertions", allAssertions.size())
            .log();

        return allAssertions;
    }

    @Override
    public List<Assertion> extractFromText(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        try {
            // Build prompt for LLM
            String userPrompt = buildExtractionPrompt(text, source);

            // Call LLM
            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(EXTRACTION_SYSTEM_PROMPT)
                .temperature(0.1) // Low temperature for consistent extraction
                .maxTokens(2000)
                .build();

            LLMResponse response = promptSender.send(request);

            // Parse JSON response
            List<AssertionDto> dtos = parseAssertionResponse(response.responseText());

            // Convert to Assertion objects
            List<Assertion> assertions = new ArrayList<>();
            for (AssertionDto dto : dtos) {
                Assertion assertion = Assertion.of(
                    generateAssertionId(dto.text()),
                    dto.text(),
                    parseAssertionType(dto.type()),
                    source,
                    relatedField
                );
                assertions.add(assertion);
            }

            log.debug("Extracted assertions from text using LLM")
                .field("source", source)
                .field("assertionCount", assertions.size())
                .log();

            return assertions;

        } catch (Exception e) {
            log.error("LLM assertion extraction failed, falling back to simple splitting")
                .field("source", source)
                .exception(e)
                .log();

            // Fallback to simple sentence splitting
            return fallbackExtraction(text, source, relatedField);
        }
    }

    /**
     * Builds the extraction prompt for the LLM.
     */
    private String buildExtractionPrompt(String text, Assertion.AssertionSource source) {
        return String.format("""
            Extract atomic assertions from the following %s text:

            ---
            %s
            ---

            Return ONLY a JSON array of assertions. No explanation.
            """,
            source.toString().toLowerCase().replace("_", " "),
            text
        );
    }

    /**
     * Parses the LLM JSON response into assertion DTOs.
     */
    private List<AssertionDto> parseAssertionResponse(String responseText) throws Exception {
        // Clean response (remove markdown code blocks if present)
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

        // Parse JSON array
        return objectMapper.readValue(
            jsonText,
            objectMapper.getTypeFactory().constructCollectionType(List.class, AssertionDto.class)
        );
    }

    /**
     * Parses assertion type from string.
     */
    private Assertion.AssertionType parseAssertionType(String typeStr) {
        try {
            return Assertion.AssertionType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown assertion type: " + typeStr + ", defaulting to OBSERVATION")
                .log();
            return Assertion.AssertionType.OBSERVATION;
        }
    }

    /**
     * Generates a unique assertion ID.
     */
    private String generateAssertionId(String text) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        int textHash = Math.abs(text.hashCode() % 10000);
        return String.format("assert-llm-%s-%04d", uuid, textHash);
    }

    /**
     * Fallback to simple extraction if LLM fails.
     */
    private List<Assertion> fallbackExtraction(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        // Simple sentence splitting fallback
        String[] sentences = text.split("[.!?]+");
        List<Assertion> assertions = new ArrayList<>();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (!sentence.isEmpty()) {
                assertions.add(Assertion.of(
                    generateAssertionId(sentence),
                    sentence,
                    Assertion.AssertionType.OBSERVATION, // Default type
                    source,
                    relatedField
                ));
            }
        }

        return assertions;
    }

    /**
     * DTO for parsing LLM JSON response.
     */
    private static class AssertionDto {
        public String text;
        public String type;

        // Jackson needs default constructor
        public AssertionDto() {}

        public AssertionDto(String text, String type) {
            this.text = text;
            this.type = type;
        }
    }
}
