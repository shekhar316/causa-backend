package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.*;
import com.causa.core.services.validation.AssertionExtractor;
import com.causa.core.services.validation.EvidenceMatcher;
import com.causa.core.services.validation.RcaValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of RCA validation using assertion-driven approach.
 *
 * <p>Validates each assertion independently and aggregates results
 * into a comprehensive validation summary.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RcaValidatorImpl implements RcaValidator {

    private static final CausaLogger log = CausaLogger.getLogger(RcaValidatorImpl.class);

    // Default confidence thresholds
    private static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    private static final double STRONG_SUPPORT_THRESHOLD = 0.8;
    private static final double WEAK_SUPPORT_THRESHOLD = 0.4;

    private final AssertionExtractor assertionExtractor;
    private final EvidenceMatcher evidenceMatcher;
    private final Optional<com.causa.core.services.validation.AssertionAnalyzer> assertionAnalyzer;

    @Inject
    public RcaValidatorImpl(
        AssertionExtractor assertionExtractor,
        EvidenceMatcher evidenceMatcher,
        Optional<com.causa.core.services.validation.AssertionAnalyzer> assertionAnalyzer
    ) {
        this.assertionExtractor = assertionExtractor;
        this.evidenceMatcher = evidenceMatcher;
        this.assertionAnalyzer = assertionAnalyzer;
    }

    @Override
    public ValidatedRCA validate(RootCauseAnalysis rca, String diagnosticContext) {
        return validate(rca, diagnosticContext, DEFAULT_MIN_CONFIDENCE);
    }

    @Override
    public ValidatedRCA validate(
        RootCauseAnalysis rca,
        String diagnosticContext,
        double minimumConfidence
    ) {
        log.info("Starting RCA validation")
            .field("issueTitle", rca.issueTitle())
            .field("anomalyType", rca.anomalyType())
            .field("minimumConfidence", minimumConfidence)
            .log();

        Instant startTime = Instant.now();

        // Step 1: Extract assertions from RCA
        List<Assertion> assertions = assertionExtractor.extractAssertions(rca);

        log.info("Assertions extracted")
            .field("totalAssertions", assertions.size())
            .log();

        // Step 2 & 3: Validate assertions
        List<ValidationResult> validationResults;

        // Use LLM Assertion Analyzer if available (recommended)
        if (assertionAnalyzer.isPresent()) {
            log.info("Using LLM assertion analyzer for intelligent validation")
                .field("assertionCount", assertions.size())
                .log();

            validationResults = assertionAnalyzer.get().analyzeAll(assertions, diagnosticContext);

        } else {
            // Fallback to rule-based evidence matching
            log.info("Using rule-based evidence matcher (LLM analyzer not available)")
                .field("assertionCount", assertions.size())
                .log();

            Map<String, List<Evidence>> evidenceMap = evidenceMatcher.findEvidenceForAll(
                assertions,
                diagnosticContext
            );

            validationResults = new ArrayList<>();
            for (Assertion assertion : assertions) {
                List<Evidence> evidence = evidenceMap.getOrDefault(assertion.id(), List.of());
                ValidationResult result = validateAssertion(assertion, evidence, minimumConfidence);
                validationResults.add(result);
            }
        }

        // Step 4: Build validated RCA
        ValidatedRCA validatedRCA = ValidatedRCA.builder()
            .originalRca(rca)
            .validationResults(validationResults)
            .validatedAt(startTime)
            .build();

        log.info("RCA validation completed")
            .field("issueTitle", rca.issueTitle())
            .field("validationSummary", validatedRCA.summary().toSummaryString())
            .field("isValid", validatedRCA.isValid())
            .field("isHighConfidence", validatedRCA.isHighConfidence())
            .log();

        return validatedRCA;
    }

    /**
     * Validates a single assertion against evidence.
     */
    private ValidationResult validateAssertion(
        Assertion assertion,
        List<Evidence> evidence,
        double minimumConfidence
    ) {
        log.debug("Validating assertion")
            .field("assertionId", assertion.id())
            .field("assertionType", assertion.type())
            .field("evidenceCount", evidence.size())
            .log();

        // Recommendations don't need validation - they are suggestions
        if (assertion.type() == Assertion.AssertionType.RECOMMENDATION) {
            return ValidationResult.unknown(
                assertion,
                "Recommendations are not validated against evidence"
            );
        }

        // No evidence found
        if (evidence.isEmpty()) {
            return ValidationResult.unknown(
                assertion,
                "No evidence found in diagnostic context"
            );
        }

        // Separate supporting vs refuting evidence
        List<Evidence> supportingEvidence = new ArrayList<>();
        List<Evidence> refutingEvidence = new ArrayList<>();

        // For now, all found evidence is considered supporting
        // Future: Implement contradiction detection
        supportingEvidence.addAll(evidence);

        // Calculate confidence based on evidence strength
        double confidence = calculateConfidence(supportingEvidence, refutingEvidence);

        // Determine validation status
        ValidationResult.ValidationStatus status = determineStatus(
            confidence,
            supportingEvidence.size(),
            refutingEvidence.size()
        );

        // Generate explanation
        String explanation = generateExplanation(
            status,
            confidence,
            supportingEvidence.size(),
            refutingEvidence.size()
        );

        return new ValidationResult(
            assertion,
            status,
            confidence,
            supportingEvidence,
            refutingEvidence,
            java.util.Optional.of(explanation)
        );
    }

    /**
     * Calculates confidence score based on evidence.
     */
    private double calculateConfidence(
        List<Evidence> supporting,
        List<Evidence> refuting
    ) {
        if (supporting.isEmpty() && refuting.isEmpty()) {
            return 0.0;
        }

        // Calculate weighted support score
        double supportScore = supporting.stream()
            .mapToDouble(Evidence::relevanceScore)
            .average()
            .orElse(0.0);

        // Calculate weighted refute score
        double refuteScore = refuting.stream()
            .mapToDouble(Evidence::relevanceScore)
            .average()
            .orElse(0.0);

        // Boost confidence based on evidence count
        int totalEvidence = supporting.size() + refuting.size();
        double countBoost = Math.min(0.15, totalEvidence * 0.05);

        // Net confidence
        double confidence = supportScore - refuteScore + countBoost;

        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Determines validation status based on confidence and evidence counts.
     */
    private ValidationResult.ValidationStatus determineStatus(
        double confidence,
        int supportingCount,
        int refutingCount
    ) {
        // Strong support
        if (confidence >= STRONG_SUPPORT_THRESHOLD && supportingCount > 0) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        }

        // Mixed evidence
        if (supportingCount > 0 && refutingCount > 0) {
            return ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
        }

        // Weak support
        if (confidence >= WEAK_SUPPORT_THRESHOLD && supportingCount > 0) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        }

        // Strong refutation
        if (refutingCount > supportingCount) {
            return ValidationResult.ValidationStatus.UNSUPPORTED;
        }

        // Unknown
        return ValidationResult.ValidationStatus.UNKNOWN;
    }

    /**
     * Generates human-readable explanation of validation.
     */
    private String generateExplanation(
        ValidationResult.ValidationStatus status,
        double confidence,
        int supportingCount,
        int refutingCount
    ) {
        return switch (status) {
            case SUPPORTED -> String.format(
                "Assertion supported by %d evidence piece%s with %.0f%% confidence",
                supportingCount,
                supportingCount == 1 ? "" : "s",
                confidence * 100
            );
            case PARTIALLY_SUPPORTED -> String.format(
                "Assertion partially supported: %d supporting, %d refuting evidence (%.0f%% confidence)",
                supportingCount,
                refutingCount,
                confidence * 100
            );
            case UNSUPPORTED -> String.format(
                "Assertion contradicted by %d evidence piece%s",
                refutingCount,
                refutingCount == 1 ? "" : "s"
            );
            case UNKNOWN -> "Insufficient evidence to validate assertion";
        };
    }
}
