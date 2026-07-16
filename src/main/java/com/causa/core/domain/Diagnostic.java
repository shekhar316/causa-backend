package com.causa.core.domain;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;

import java.time.Instant;
import java.util.Objects;

/**
 * Diagnostic Domain Model
 *
 * <p>Represents the LLM-based diagnostic analysis result for an alert.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>{@code PENDING}     — row created immediately before LLM pipeline starts</li>
 *   <li>{@code IN_PROGRESS} — RCA completed and persisted; validation not yet run</li>
 *   <li>{@code COMPLETED}   — (future) RCA validated against collected context</li>
 *   <li>{@code FAILED}      — pipeline threw an unrecoverable exception</li>
 * </ol>
 *
 * @since 0.0.1
 */
public final class Diagnostic {

    private final String diagnosticId;
    private final String alertId;
    private final DiagnosticStatus status;
    private final Instant generatedAt;

    // Populated once RCA completes (status → IN_PROGRESS)
    private final Float confidenceScore;      // average of rca + solution scores
    private final FaultDomain faultDomain;
    private final String rootCauseAnalysis;   // full RCA JSON string from LLM

    private final String issueTitle;
    private final String issueDescription;
    private final String modelUsed;
    private final Double rcaConfidenceScore;
    private final Double solutionConfidenceScore;
    private final String confidenceSummary;
    private final String llmNotes;

    // Populated once validation completes (status → COMPLETED) — future
    private final String validationResult;

    private Diagnostic(Builder builder) {
        this.diagnosticId          = Objects.requireNonNull(builder.diagnosticId,  "diagnosticId cannot be null");
        this.alertId               = Objects.requireNonNull(builder.alertId,        "alertId cannot be null");
        this.status                = Objects.requireNonNull(builder.status,         "status cannot be null");
        this.generatedAt           = Objects.requireNonNull(builder.generatedAt,    "generatedAt cannot be null");
        this.confidenceScore       = builder.confidenceScore;
        this.faultDomain           = builder.faultDomain;
        this.rootCauseAnalysis     = builder.rootCauseAnalysis;
        this.issueTitle            = builder.issueTitle;
        this.issueDescription      = builder.issueDescription;
        this.modelUsed             = builder.modelUsed;
        this.rcaConfidenceScore    = builder.rcaConfidenceScore;
        this.solutionConfidenceScore = builder.solutionConfidenceScore;
        this.confidenceSummary     = builder.confidenceSummary;
        this.llmNotes              = builder.llmNotes;
        this.validationResult      = builder.validationResult;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getDiagnosticId()           { return diagnosticId; }
    public String getAlertId()                { return alertId; }
    public DiagnosticStatus getStatus()       { return status; }
    public Instant getGeneratedAt()           { return generatedAt; }
    public Float getConfidenceScore()         { return confidenceScore; }
    public FaultDomain getFaultDomain()       { return faultDomain; }
    public String getRootCauseAnalysis()      { return rootCauseAnalysis; }
    public String getIssueTitle()             { return issueTitle; }
    public String getIssueDescription()       { return issueDescription; }
    /** LLM model name used to generate this diagnostic (e.g. {@code claude-sonnet-4-6}). */
    public String getModelUsed()              { return modelUsed; }
    public Double getRcaConfidenceScore()     { return rcaConfidenceScore; }
    public Double getSolutionConfidenceScore(){ return solutionConfidenceScore; }
    public String getConfidenceSummary()      { return confidenceSummary; }
    public String getLlmNotes()               { return llmNotes; }
    /** Validation outcome — null until validation framework runs. */
    public String getValidationResult()       { return validationResult; }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String diagnosticId;
        private String alertId;
        private DiagnosticStatus status;
        private Instant generatedAt;
        private Float confidenceScore;
        private FaultDomain faultDomain;
        private String rootCauseAnalysis;
        private String issueTitle;
        private String issueDescription;
        private String modelUsed;
        private Double rcaConfidenceScore;
        private Double solutionConfidenceScore;
        private String confidenceSummary;
        private String llmNotes;
        private String validationResult;

        private Builder() {}

        public Builder diagnosticId(String v)           { this.diagnosticId = v; return this; }
        public Builder alertId(String v)                { this.alertId = v; return this; }
        public Builder status(DiagnosticStatus v)       { this.status = v; return this; }
        public Builder generatedAt(Instant v)           { this.generatedAt = v; return this; }
        public Builder confidenceScore(Float v)         { this.confidenceScore = v; return this; }
        public Builder faultDomain(FaultDomain v)       { this.faultDomain = v; return this; }
        public Builder rootCauseAnalysis(String v)      { this.rootCauseAnalysis = v; return this; }
        public Builder issueTitle(String v)             { this.issueTitle = v; return this; }
        public Builder issueDescription(String v)       { this.issueDescription = v; return this; }
        public Builder modelUsed(String v)              { this.modelUsed = v; return this; }
        public Builder rcaConfidenceScore(Double v)     { this.rcaConfidenceScore = v; return this; }
        public Builder solutionConfidenceScore(Double v){ this.solutionConfidenceScore = v; return this; }
        public Builder confidenceSummary(String v)      { this.confidenceSummary = v; return this; }
        public Builder llmNotes(String v)               { this.llmNotes = v; return this; }
        public Builder validationResult(String v)       { this.validationResult = v; return this; }

        public Diagnostic build() { return new Diagnostic(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(diagnosticId, ((Diagnostic) o).diagnosticId);
    }

    @Override
    public int hashCode() { return Objects.hash(diagnosticId); }

    @Override
    public String toString() {
        return "Diagnostic{diagnosticId='" + diagnosticId + "', alertId='" + alertId
            + "', status=" + status + ", faultDomain=" + faultDomain
            + ", rcaConfidence=" + rcaConfidenceScore + '}';
    }
}
