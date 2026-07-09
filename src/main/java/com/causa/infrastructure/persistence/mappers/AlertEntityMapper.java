package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.JsonUtils;
import com.causa.core.domain.Alert;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Alert Entity Mapper
 *
 * <p>Maps between Alert domain model and AlertEntity JPA entity.
 * <p>Uses JsonUtils for Map&lt;String, String&gt; ↔ JsonNode conversion.
 *
 * @since 0.0.1
 */
public final class AlertEntityMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private AlertEntityMapper() {
        // Prevent instantiation
    }

    /**
     * Converts domain Alert to AlertEntity.
     *
     * @param alert the domain alert
     * @return the alert entity
     */
    public static AlertEntity toEntity(Alert alert) {
        if (alert == null) {
            return null;
        }

        AlertEntity entity = new AlertEntity();
        // PK: use the application-generated alertId
        entity.setId(alert.getAlertId());
        entity.setSourceAlertId(alert.getAlertId());
        entity.setAlertName(alert.getAlertName());

        // Map Instant → OffsetDateTime for the entity column
        if (alert.getTimestamp() != null) {
            entity.setAlertTimestamp(alert.getTimestamp().atOffset(ZoneOffset.UTC));
        }

        entity.setSeverity(alert.getSeverity() != null ? alert.getSeverity().getValue() : null);
        entity.setStatus(alert.getStatus() != null ? alert.getStatus().getValue() : null);
        entity.setContainerName(alert.getContainerName() != null ? alert.getContainerName() : "");

        // Pack pod/namespace/container into containerInfo JSONB
        ObjectNode containerInfo = objectMapper.createObjectNode();
        containerInfo.put("pod",       alert.getPodName() != null       ? alert.getPodName()       : "");
        containerInfo.put("namespace", alert.getNamespace() != null     ? alert.getNamespace()     : "");
        containerInfo.put("container", alert.getContainerName() != null ? alert.getContainerName() : "");
        entity.setContainerInfo(containerInfo);

        // Pack labels + annotations into alertMetadata JSONB
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.set("labels",      JsonUtils.mapToJsonNode(alert.getLabels()));
        metadata.set("annotations", JsonUtils.mapToJsonNode(alert.getAnnotations()));
        entity.setAlertMetadata(metadata);

        return entity;
    }

    /**
     * Converts AlertEntity to domain Alert.
     *
     * @param entity the alert entity
     * @return the domain alert
     */
    public static Alert toDomain(AlertEntity entity) {
        if (entity == null) {
            return null;
        }

        // Unpack containerInfo JSONB
        String pod       = null;
        String namespace = null;
        String container = entity.getContainerName();
        if (entity.getContainerInfo() != null) {
            pod       = entity.getContainerInfo().path("pod").asText(null);
            namespace = entity.getContainerInfo().path("namespace").asText(null);
            container = entity.getContainerInfo().path("container").asText(container);
        }

        // Unpack alertMetadata JSONB
        java.util.Map<String, String> labels      = null;
        java.util.Map<String, String> annotations = null;
        if (entity.getAlertMetadata() != null) {
            labels      = JsonUtils.jsonNodeToMap(entity.getAlertMetadata().get("labels"));
            annotations = JsonUtils.jsonNodeToMap(entity.getAlertMetadata().get("annotations"));
        }

        // Map OffsetDateTime → Instant
        Instant timestamp = null;
        if (entity.getAlertTimestamp() != null) {
            timestamp = entity.getAlertTimestamp().toInstant();
        }

        return Alert.builder()
            .alertId(entity.getId())
            .timestamp(timestamp)
            .alertName(entity.getAlertName())
            .severity(AlertSeverity.fromString(entity.getSeverity()))
            .podName(pod)
            .containerName(container)
            .namespace(namespace)
            .status(AlertStatus.fromString(entity.getStatus()))
            .hasDiagnostics(false)
            .labels(labels)
            .annotations(annotations)
            .build();
    }
}
