package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.request.DiagnosticTriggerRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.utils.AlertNameUtils;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic Trigger Mapper
 *
 * <p>Converts a {@link DiagnosticTriggerRequest} into an {@link AlertWebhookRequest} so the
 * manual trigger path can delegate entirely to the existing webhook pipeline.
 *
 * <p>Labels carry the minimum fields required by the webhook validator ({@code alertname},
 * {@code container}, {@code namespace}, {@code severity}).  All richer workload context
 * ({@code pod_name}, {@code container_name}, {@code workload_name}, etc.) goes into
 * annotations, which is where {@link AlertMapper} reads them first.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticTriggerMapper {

    /**
     * Builds a synthetic {@link AlertWebhookRequest} from a manual trigger request.
     *
     * <p>The generated alert name follows the pattern:
     * {@code manual-analysis-trigger-<epoch-seconds>-<3-char-suffix>}.
     *
     * @param request the manual trigger request
     * @return a fully-populated AlertWebhookRequest ready for the webhook pipeline
     */
    public AlertWebhookRequest toWebhookRequest(DiagnosticTriggerRequest request) {
        String alertName = AlertNameUtils.generateManualAlertName();
        String severity  = (request.getSeverity() != null && !request.getSeverity().isBlank())
                ? request.getSeverity()
                : AlertConstants.ManualTrigger.DEFAULT_SEVERITY;

        // Labels — only what the webhook validator requires
        Map<String, String> labels = new HashMap<>();
        labels.put(AlertConstants.Labels.ALERT_NAME, alertName);
        labels.put(AlertConstants.Labels.SEVERITY,   severity);
        labels.put(AlertConstants.Labels.NAMESPACE,
                request.getNamespace() != null ? request.getNamespace() : "");
        labels.put(AlertConstants.Labels.CONTAINER,
                request.getContainer() != null ? request.getContainer() : "");

        // Annotations — full workload context read by AlertMapper
        Map<String, String> annotations = new HashMap<>();
        annotations.put(AlertConstants.Labels.ALERT_SOURCE, AlertConstants.ManualTrigger.ALERT_SOURCE);
        if (request.getPodName()      != null) annotations.put(AlertConstants.Labels.POD_NAME,      request.getPodName());
        if (request.getContainer()    != null) annotations.put(AlertConstants.Labels.CONTAINER_NAME, request.getContainer());
        if (request.getWorkloadName() != null) annotations.put(AlertConstants.Labels.WORKLOAD_NAME,  request.getWorkloadName());
        if (request.getWorkloadType() != null) annotations.put(AlertConstants.Labels.WORKLOAD_TYPE,  request.getWorkloadType());
        if (request.getClusterName()  != null) annotations.put(AlertConstants.Labels.CLUSTER_NAME,   request.getClusterName());

        AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
        item.setStatus(AlertConstants.Webhook.STATUS_FIRING);
        item.setLabels(labels);
        item.setAnnotations(annotations);
        item.setStartsAt(Instant.now().toString());

        AlertWebhookRequest webhookRequest = new AlertWebhookRequest();
        webhookRequest.setVersion(AlertConstants.Webhook.ALERTMANAGER_VERSION);
        webhookRequest.setStatus(AlertConstants.Webhook.STATUS_FIRING);
        webhookRequest.setReceiver(AlertConstants.ManualTrigger.WEBHOOK_RECEIVER);
        webhookRequest.setAlerts(List.of(item));

        return webhookRequest;
    }
}
