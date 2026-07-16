package com.causa.api.validators;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Alert Webhook Validator
 *
 * <p>Validates incoming Prometheus Alertmanager webhook requests.
 * <p>Returns a list of validation error messages (empty list means valid).
 *
 * <p>Platform-aware: {@code container} and {@code namespace} labels are only required
 * when the effective platform is {@code cluster}. The effective platform is resolved
 * as: alert label {@code platform} → fallback to app-level {@code configuredPlatform}
 * → default {@code "cluster"}.
 *
 * @since 0.0.1
 */
public final class AlertWebhookValidator {

    private AlertWebhookValidator() {
        // Prevent instantiation
    }

    /**
     * Validates the incoming Alertmanager webhook request using the app-level platform
     * as the fallback when no {@code platform} label is present in alert labels.
     *
     * @param request            the webhook request to validate
     * @param configuredPlatform the app-level platform ({@code causa.platform}); may be null
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validate(AlertWebhookRequest request, String configuredPlatform) {
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
                validateAlertItem(request.getAlerts().get(i), i, errors, configuredPlatform);
            }
        }

        return errors;
    }

    /**
     * Validates the incoming Alertmanager webhook request.
     * Defaults effective platform to {@code "cluster"} when no label is present.
     *
     * @param request the webhook request to validate
     * @return list of validation error messages (empty if valid)
     * @deprecated Prefer {@link #validate(AlertWebhookRequest, String)} to pass the
     *             app-level configured platform so VM deployments are validated correctly.
     */
    @Deprecated
    public static List<String> validate(AlertWebhookRequest request) {
        return validate(request, AlertConstants.Labels.PLATFORM_CLUSTER);
    }

    /**
     * Validates a single alert item.
     *
     * <p>Effective platform resolution order:
     * <ol>
     *   <li>Alert label {@code platform} (if present)</li>
     *   <li>{@code configuredPlatform} from app config ({@code causa.platform})</li>
     *   <li>Default: {@code "cluster"}</li>
     * </ol>
     *
     * @param item               the alert item
     * @param index              the item index (for error reporting)
     * @param errors             the list to accumulate errors into
     * @param configuredPlatform the app-level platform fallback
     */
    private static void validateAlertItem(AlertWebhookRequest.AlertItem item,
                                          int index,
                                          List<String> errors,
                                          String configuredPlatform) {
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

        // Effective platform: label → app config → default cluster
        String effectivePlatform = item.getLabels().getOrDefault(
            AlertConstants.Labels.PLATFORM,
            configuredPlatform != null ? configuredPlatform : AlertConstants.Labels.PLATFORM_CLUSTER);

        // container and namespace are only required for cluster platform
        if (AlertConstants.Labels.PLATFORM_CLUSTER.equalsIgnoreCase(effectivePlatform)) {
            if (!item.getLabels().containsKey(AlertConstants.Labels.CONTAINER)) {
                errors.add("alerts[" + index + "].labels must contain '"
                    + AlertConstants.Labels.CONTAINER + "' for platform=cluster");
            }
            if (!item.getLabels().containsKey(AlertConstants.Labels.NAMESPACE)) {
                errors.add("alerts[" + index + "].labels must contain '"
                    + AlertConstants.Labels.NAMESPACE + "' for platform=cluster");
            }
        }
    }
}
