package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.core.domain.Diagnostic;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Diagnostic Entity Mapper
 *
 * <p>Maps between Diagnostic domain model and DiagnosticEntity JPA entity.
 * <p>Bridges field-name differences between the domain model and the DB schema.
 *
 * <ul>
 *   <li>{@code diagnosticId}   ↔ {@code id}</li>
 *   <li>{@code generatedAt}    ↔ stored in {@code diagnosticsMetadata.generatedAt}</li>
 *   <li>{@code confidenceScore}↔ stored in {@code confidenceInfo.score}</li>
 *   <li>{@code faultDomain}    ↔ {@code issueType}</li>
 *   <li>{@code rootCauseAnalysis} ↔ {@code rootCauseSummary}</li>
 * </ul>
 *
 * @since 0.0.1
 */
public final class DiagnosticEntityMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private DiagnosticEntityMapper() {
        // Prevent instantiation
    }

    /**
     * Converts domain Diagnostic to DiagnosticEntity.
     *
     * @param diagnostic the domain diagnostic
     * @return the diagnostic entity
     */
    public static DiagnosticEntity toEntity(Diagnostic diagnostic) {
        if (diagnostic == null) {
            return null;
        }

        DiagnosticEntity entity = new DiagnosticEntity();
        entity.setId(diagnostic.getDiagnosticId());

        // FK reference — Hibernate uses the ID only; no SELECT issued
        AlertEntity alertRef = new AlertEntity();
        alertRef.setId(diagnostic.getAlertId());
        entity.setAlert(alertRef);

        entity.setStatus(diagnostic.getStatus().getValue());

        // faultDomain → issueType
        if (diagnostic.getFaultDomain() != null) {
            entity.setIssueType(diagnostic.getFaultDomain().getValue());
        }

        // issue_title / issue_description
        entity.setIssueTitle(diagnostic.getIssueTitle());
        entity.setIssueDescription(diagnostic.getIssueDescription());

        // rootCauseAnalysis (full RCA JSON) → root_cause_summary
        if (diagnostic.getRootCauseAnalysis() != null) {
            entity.setRootCauseSummary(diagnostic.getRootCauseAnalysis());
        }

        // confidence_info JSONB:
        //   { "score": <avg>, "rca_score": <double>, "solution_score": <double>, "summary": "..." }
        ObjectNode confidenceNode = objectMapper.createObjectNode();
        if (diagnostic.getConfidenceScore() != null) {
            confidenceNode.put("score", diagnostic.getConfidenceScore());
        }
        if (diagnostic.getRcaConfidenceScore() != null) {
            confidenceNode.put("rca_score", diagnostic.getRcaConfidenceScore());
        }
        if (diagnostic.getSolutionConfidenceScore() != null) {
            confidenceNode.put("solution_score", diagnostic.getSolutionConfidenceScore());
        }
        if (diagnostic.getConfidenceSummary() != null) {
            confidenceNode.put("summary", diagnostic.getConfidenceSummary());
        }
        if (!confidenceNode.isEmpty()) {
            entity.setConfidenceInfo(confidenceNode);
        }

        // llm_info JSONB: { "model": "...", "llm_notes": "..." }
        if (diagnostic.getModelUsed() != null || diagnostic.getLlmNotes() != null) {
            ObjectNode llmNode = objectMapper.createObjectNode();
            if (diagnostic.getModelUsed() != null) {
                llmNode.put("model", diagnostic.getModelUsed());
            }
            if (diagnostic.getLlmNotes() != null) {
                llmNode.put("llm_notes", diagnostic.getLlmNotes());
            }
            entity.setLlmInfo(llmNode);
        }

        // validation_result (set after validation framework runs)
        if (diagnostic.getValidationResult() != null) {
            entity.setValidationResult(diagnostic.getValidationResult());
        }

        // diagnostics_metadata JSONB: { "generatedAt": <epochMilli> }
        if (diagnostic.getGeneratedAt() != null) {
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("generatedAt", diagnostic.getGeneratedAt().toEpochMilli());
            entity.setDiagnosticsMetadata(meta);
        }

        return entity;
    }

    /**
     * Converts DiagnosticEntity to domain Diagnostic.
     *
     * @param entity the diagnostic entity
     * @return the domain diagnostic
     */
    public static Diagnostic toDomain(DiagnosticEntity entity) {
        if (entity == null) {
            return null;
        }

        Diagnostic.Builder builder = Diagnostic.builder()
            .diagnosticId(entity.getId())
            .alertId(entity.getAlertId())
            .status(DiagnosticStatus.fromString(entity.getStatus()))
            .issueTitle(entity.getIssueTitle())
            .issueDescription(entity.getIssueDescription());

        // issueType → faultDomain
        if (entity.getIssueType() != null) {
            builder.faultDomain(FaultDomain.fromString(entity.getIssueType()));
        }

        // root_cause_summary → rootCauseAnalysis
        if (entity.getRootCauseSummary() != null) {
            builder.rootCauseAnalysis(entity.getRootCauseSummary());
        }

        // confidence_info JSONB → confidence fields
        JsonNode ci = entity.getConfidenceInfo();
        if (ci != null) {
            if (ci.has("score"))          builder.confidenceScore(ci.get("score").floatValue());
            if (ci.has("rca_score"))      builder.rcaConfidenceScore(ci.get("rca_score").doubleValue());
            if (ci.has("solution_score")) builder.solutionConfidenceScore(ci.get("solution_score").doubleValue());
            if (ci.has("summary"))        builder.confidenceSummary(ci.get("summary").asText(null));
        }

        // llm_info JSONB → model + llmNotes
        JsonNode llmInfo = entity.getLlmInfo();
        if (llmInfo != null) {
            if (llmInfo.has("model"))     builder.modelUsed(llmInfo.get("model").asText(null));
            if (llmInfo.has("llm_notes")) builder.llmNotes(llmInfo.get("llm_notes").asText(null));
        }

        // validation_result
        if (entity.getValidationResult() != null) {
            builder.validationResult(entity.getValidationResult());
        }

        // diagnostics_metadata JSONB → generatedAt
        JsonNode meta = entity.getDiagnosticsMetadata();
        if (meta != null && meta.has("generatedAt")) {
            builder.generatedAt(Instant.ofEpochMilli(meta.get("generatedAt").asLong()));
        } else {
            builder.generatedAt(Instant.now());
        }

        return builder.build();
    }
}
