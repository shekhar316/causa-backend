package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.request.DiagnosticTriggerRequest;
import com.causa.common.constants.AlertConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiagnosticTriggerMapper}.
 *
 * @since 0.0.1
 */
@DisplayName("DiagnosticTriggerMapper Tests")
class DiagnosticTriggerMapperTest {

    private DiagnosticTriggerMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DiagnosticTriggerMapper();
    }

    // -------------------------------------------------------------------------
    // Top-level request structure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Webhook request structure")
    class WebhookRequestStructureTests {

        @Test
        @DisplayName("Should return a non-null AlertWebhookRequest")
        void shouldReturnNonNull() {
            assertNotNull(mapper.toWebhookRequest(new DiagnosticTriggerRequest()));
        }

        @Test
        @DisplayName("Should set receiver to manual-trigger constant")
        void shouldSetReceiver() {
            AlertWebhookRequest result = mapper.toWebhookRequest(new DiagnosticTriggerRequest());
            assertEquals(AlertConstants.ManualTrigger.WEBHOOK_RECEIVER, result.getReceiver());
        }

        @Test
        @DisplayName("Should produce exactly one alert item")
        void shouldProduceOneAlertItem() {
            AlertWebhookRequest result = mapper.toWebhookRequest(new DiagnosticTriggerRequest());
            List<AlertWebhookRequest.AlertItem> alerts = result.getAlerts();
            assertNotNull(alerts);
            assertEquals(1, alerts.size());
        }
    }

    // -------------------------------------------------------------------------
    // Alert item — labels
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Alert item labels")
    class AlertItemLabelsTests {

        @Test
        @DisplayName("Should set alertname label starting with manual prefix")
        void shouldSetAlertNameWithManualPrefix() {
            AlertWebhookRequest.AlertItem item = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0);
            String alertName = item.getLabels().get(AlertConstants.Labels.ALERT_NAME);
            assertNotNull(alertName);
            assertTrue(alertName.startsWith(AlertConstants.ManualTrigger.ALERT_NAME_PREFIX),
                    "Expected alertname to start with manual prefix but was: " + alertName);
        }

        @Test
        @DisplayName("Should set namespace label from request")
        void shouldSetNamespaceLabel() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setNamespace("staging");
            Map<String, String> labels = mapper.toWebhookRequest(req).getAlerts().get(0).getLabels();
            assertEquals("staging", labels.get(AlertConstants.Labels.NAMESPACE));
        }

        @Test
        @DisplayName("Should set namespace label to empty string when null in request")
        void shouldSetNamespaceLabelEmptyWhenNull() {
            Map<String, String> labels = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getLabels();
            assertEquals("", labels.get(AlertConstants.Labels.NAMESPACE));
        }

        @Test
        @DisplayName("Should set container label from request")
        void shouldSetContainerLabel() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setContainer("my-container");
            Map<String, String> labels = mapper.toWebhookRequest(req).getAlerts().get(0).getLabels();
            assertEquals("my-container", labels.get(AlertConstants.Labels.CONTAINER));
        }

        @Test
        @DisplayName("Should set container label to empty string when null in request")
        void shouldSetContainerLabelEmptyWhenNull() {
            Map<String, String> labels = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getLabels();
            assertEquals("", labels.get(AlertConstants.Labels.CONTAINER));
        }

        @Test
        @DisplayName("Should use request severity when provided")
        void shouldUseProvidedSeverity() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setSeverity("warning");
            Map<String, String> labels = mapper.toWebhookRequest(req).getAlerts().get(0).getLabels();
            assertEquals("warning", labels.get(AlertConstants.Labels.SEVERITY));
        }

        @Test
        @DisplayName("Should default severity to 'critical' when null in request")
        void shouldDefaultSeverityToNullInRequest() {
            Map<String, String> labels = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getLabels();
            assertEquals(AlertConstants.ManualTrigger.DEFAULT_SEVERITY,
                    labels.get(AlertConstants.Labels.SEVERITY));
        }

        @Test
        @DisplayName("Should default severity to 'critical' when blank in request")
        void shouldDefaultSeverityToBlankInRequest() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setSeverity("   ");
            Map<String, String> labels = mapper.toWebhookRequest(req).getAlerts().get(0).getLabels();
            assertEquals(AlertConstants.ManualTrigger.DEFAULT_SEVERITY,
                    labels.get(AlertConstants.Labels.SEVERITY));
        }
    }

    // -------------------------------------------------------------------------
    // Alert item — annotations
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Alert item annotations")
    class AlertItemAnnotationsTests {

        @Test
        @DisplayName("Should set alert_source annotation to manual-trigger")
        void shouldSetAlertSourceAnnotation() {
            Map<String, String> annotations = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getAnnotations();
            assertEquals(AlertConstants.ManualTrigger.ALERT_SOURCE,
                    annotations.get(AlertConstants.Labels.ALERT_SOURCE));
        }

        @Test
        @DisplayName("Should set pod_name annotation when provided")
        void shouldSetPodNameAnnotation() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setPodName("my-pod-abc");
            Map<String, String> annotations = mapper.toWebhookRequest(req).getAlerts().get(0).getAnnotations();
            assertEquals("my-pod-abc", annotations.get(AlertConstants.Labels.POD_NAME));
        }

        @Test
        @DisplayName("Should not set pod_name annotation when null in request")
        void shouldNotSetPodNameAnnotationWhenNull() {
            Map<String, String> annotations = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getAnnotations();
            assertFalse(annotations.containsKey(AlertConstants.Labels.POD_NAME));
        }

        @Test
        @DisplayName("Should set container_name annotation when container provided")
        void shouldSetContainerNameAnnotation() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setContainer("app-container");
            Map<String, String> annotations = mapper.toWebhookRequest(req).getAlerts().get(0).getAnnotations();
            assertEquals("app-container", annotations.get(AlertConstants.Labels.CONTAINER_NAME));
        }

        @Test
        @DisplayName("Should set workload_name annotation when provided")
        void shouldSetWorkloadNameAnnotation() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setWorkloadName("my-service");
            Map<String, String> annotations = mapper.toWebhookRequest(req).getAlerts().get(0).getAnnotations();
            assertEquals("my-service", annotations.get(AlertConstants.Labels.WORKLOAD_NAME));
        }

        @Test
        @DisplayName("Should not set workload_name annotation when null in request")
        void shouldNotSetWorkloadNameAnnotationWhenNull() {
            Map<String, String> annotations = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getAnnotations();
            assertFalse(annotations.containsKey(AlertConstants.Labels.WORKLOAD_NAME));
        }

        @Test
        @DisplayName("Should set workload_type annotation when provided")
        void shouldSetWorkloadTypeAnnotation() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setWorkloadType("StatefulSet");
            Map<String, String> annotations = mapper.toWebhookRequest(req).getAlerts().get(0).getAnnotations();
            assertEquals("StatefulSet", annotations.get(AlertConstants.Labels.WORKLOAD_TYPE));
        }

        @Test
        @DisplayName("Should set cluster_name annotation when provided")
        void shouldSetClusterNameAnnotation() {
            DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
            req.setClusterName("prod-cluster");
            Map<String, String> annotations = mapper.toWebhookRequest(req).getAlerts().get(0).getAnnotations();
            assertEquals("prod-cluster", annotations.get(AlertConstants.Labels.CLUSTER_NAME));
        }

        @Test
        @DisplayName("Should not set cluster_name annotation when null in request")
        void shouldNotSetClusterNameAnnotationWhenNull() {
            Map<String, String> annotations = mapper.toWebhookRequest(new DiagnosticTriggerRequest())
                    .getAlerts().get(0).getAnnotations();
            assertFalse(annotations.containsKey(AlertConstants.Labels.CLUSTER_NAME));
        }
    }
}
