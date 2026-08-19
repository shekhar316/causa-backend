package com.causa.api.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiagnosticTriggerRequest}.
 *
 * @since 0.0.1
 */
@DisplayName("DiagnosticTriggerRequest Tests")
class DiagnosticTriggerRequestTest {

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should correctly set and get namespace")
    void shouldSetAndGetNamespace() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setNamespace("production");
        assertEquals("production", req.getNamespace());
    }

    @Test
    @DisplayName("Should correctly set and get container")
    void shouldSetAndGetContainer() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setContainer("my-container");
        assertEquals("my-container", req.getContainer());
    }

    @Test
    @DisplayName("Should correctly set and get pod_name")
    void shouldSetAndGetPodName() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setPodName("my-pod-abc123");
        assertEquals("my-pod-abc123", req.getPodName());
    }

    @Test
    @DisplayName("Should correctly set and get workload_name")
    void shouldSetAndGetWorkloadName() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setWorkloadName("my-service");
        assertEquals("my-service", req.getWorkloadName());
    }

    @Test
    @DisplayName("Should correctly set and get workload_type")
    void shouldSetAndGetWorkloadType() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setWorkloadType("Deployment");
        assertEquals("Deployment", req.getWorkloadType());
    }

    @Test
    @DisplayName("Should correctly set and get cluster_name")
    void shouldSetAndGetClusterName() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setClusterName("prod-cluster");
        assertEquals("prod-cluster", req.getClusterName());
    }

    @Test
    @DisplayName("Should correctly set and get severity")
    void shouldSetAndGetSeverity() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();
        req.setSeverity("warning");
        assertEquals("warning", req.getSeverity());
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should have all fields null on default construction")
    void shouldHaveAllNullFieldsByDefault() {
        DiagnosticTriggerRequest req = new DiagnosticTriggerRequest();

        assertNull(req.getNamespace());
        assertNull(req.getContainer());
        assertNull(req.getPodName());
        assertNull(req.getWorkloadName());
        assertNull(req.getWorkloadType());
        assertNull(req.getClusterName());
        assertNull(req.getSeverity());
    }
}
