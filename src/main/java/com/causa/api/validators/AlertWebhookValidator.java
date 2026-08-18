package com.causa.api.validators;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alert Webhook Validator
 *
 * <p>Validates incoming Prometheus Alertmanager webhook requests.
 * <p>Returns a list of validation error messages (empty list means valid).
 *
 * <p>For {@code vm} platform, only {@code alertname} is required in labels,
 * and {@code workload_name} is expected in annotations.
 * For {@code cluster} platform, {@code alertname}, {@code container}, and {@code namespace} are required in labels.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertWebhookValidator {

    private final String platform;

    @Inject
    public AlertWebhookValidator(
            @ConfigProperty(name = "causa.cluster.target-cluster-type", defaultValue = "cluster") String platform) {
        this.platform = platform != null ? platform.trim().toLowerCase() : "cluster";
    }

    /**
     * Validates the incoming Alertmanager webhook request.
     *
     * @param request the webhook request to validate
     * @return list of validation error messages (empty if valid)
     */
    public List<String> validate(AlertWebhookRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request body is null");
            return errors;
        }

        if (request.getAlerts() == null || request.getAlerts().isEmpty()) {
            errors.add("Alerts array is null or empty");
        }

        // Validate Alertmanager version if present
        if (request.getVersion() != null
                && !AlertConstants.Webhook.ALERTMANAGER_VERSION.equals(request.getVersion())) {
            errors.add("Unsupported Alertmanager version: " + request.getVersion()
                + " (expected: " + AlertConstants.Webhook.ALERTMANAGER_VERSION + ")");
        }

        // Validate each alert item
        if (request.getAlerts() != null) {
            for (int i = 0; i < request.getAlerts().size(); i++) {
                validateAlertItem(request.getAlerts().get(i), i, errors);
            }
        }

        return errors;
    }

    /**
     * Validates a single alert item.
     *
     * @param item the alert item
     * @param index the item index (for error reporting)
     * @param errors the list to accumulate errors into
     */
    private void validateAlertItem(AlertWebhookRequest.AlertItem item,
                                          int index,
                                          List<String> errors) {
        if (item == null) {
            errors.add("alerts[" + index + "] is null");
            return;
        }

        if (item.getStatus() == null || item.getStatus().isBlank()) {
            errors.add("alerts[" + index + "].status is required");
        }

        if (item.getLabels() == null || item.getLabels().isEmpty()) {
            errors.add("alerts[" + index + "].labels is required");
            return;
        }

        // alertname is always required
        if (!item.getLabels().containsKey(AlertConstants.Labels.ALERT_NAME)) {
            errors.add("alerts[" + index + "].labels must contain '"
                + AlertConstants.Labels.ALERT_NAME + "'");
        }

        Map<String, String> annotations = item.getAnnotations();
        Map<String, String> labels      = item.getLabels();

        if ("vm".equals(platform)) {
            // VM platform — require workload_name (annotation first, label fallback, non-blank)
            String workloadName = getAnnotationOrLabel(annotations, labels,
                    AlertConstants.Labels.WORKLOAD_NAME, AlertConstants.Labels.WORKLOAD_NAME);
            if (workloadName == null || workloadName.isBlank()) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.WORKLOAD_NAME + "' in annotations or labels");
            }
        } else {
            // Cluster platform — require container, namespace, and pod_name
            // Each resolved annotation-first then label fallback, matching AlertMapper.
            String container = getAnnotationOrLabel(annotations, labels,
                    AlertConstants.Labels.CONTAINER_NAME, AlertConstants.Labels.CONTAINER);
            if (container == null || container.isBlank()) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.CONTAINER + "' in annotations or labels");
            }

            String namespace = getAnnotationOrLabel(annotations, labels,
                    AlertConstants.Labels.NAMESPACE, AlertConstants.Labels.NAMESPACE);
            if (namespace == null || namespace.isBlank()) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.NAMESPACE + "' in annotations or labels");
            }

            String pod = getAnnotationOrLabel(annotations, labels,
                    AlertConstants.Labels.POD_NAME, AlertConstants.Labels.POD);
            if (pod == null || pod.isBlank()) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.POD_NAME + "' in annotations or labels");
            }
        }
    }

    /**
     * Resolves a value by checking {@code annotationKey} in annotations first,
     * then {@code labelKey} in labels as fallback — matches the lookup order used
     * throughout the alert processing pipeline.
     *
     * <p>Pass the same key for both parameters when the key is identical in both maps.
     *
     * @param annotations   the annotations map (may be null)
     * @param labels        the labels map (may be null)
     * @param annotationKey the key to look up in annotations
     * @param labelKey      the key to look up in labels as fallback
     * @return the resolved value, or {@code null} if absent from both maps
     */
    public static String getAnnotationOrLabel(Map<String, String> annotations,
                                              Map<String, String> labels,
                                              String annotationKey,
                                              String labelKey) {
        if (annotations != null) {
            String value = annotations.get(annotationKey);
            if (value != null) return value;
        }
        return labels != null ? labels.get(labelKey) : null;
    }
}
