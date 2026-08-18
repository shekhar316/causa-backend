package com.causa.api.controllers;

import com.causa.api.dto.request.DiagnosticTriggerRequest;
import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.DiagnosticDetailResponse;
import com.causa.api.dto.response.DiagnosticListItemResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.DiagnosticTriggerMapper;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostics Controller
 *
 * <p>GET  /api/v1/diagnostics        — list all diagnostics (summary)
 * <p>GET  /api/v1/diagnostics/{id}   — full diagnostic detail
 * <p>POST /api/v1/diagnostics        — manually trigger a diagnostic
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Diagnostics.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DiagnosticsController {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticsController.class);

    private final DiagnosticService diagnosticService;
    private final AlertRepository alertRepository;
    private final DiagnosticTriggerMapper triggerMapper;
    private final WebhookController webhookController;
    private final String clusterName;

    @Inject
    public DiagnosticsController(DiagnosticService diagnosticService,
                                  AlertRepository alertRepository,
                                  DiagnosticTriggerMapper triggerMapper,
                                  WebhookController webhookController,
                                  @ConfigProperty(name = "causa.cluster.name", defaultValue = "default")
                                  String clusterName) {
        this.diagnosticService  = diagnosticService;
        this.alertRepository    = alertRepository;
        this.triggerMapper      = triggerMapper;
        this.webhookController  = webhookController;
        this.clusterName        = clusterName;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics
    // -------------------------------------------------------------------------

    @GET
    public Response listDiagnostics() {
        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_REQUEST).log();

        List<Diagnostic> diagnostics = diagnosticService.listDiagnostics();

        List<DiagnosticListItemResponse> items = diagnostics.stream()
            .map(d -> {
                Alert alert = alertRepository.findById(d.getAlertId()).orElse(null);
                return DiagnosticListItemResponse.from(d, alert, clusterName);
            })
            .toList();

        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_RETURNED)
            .field("count", items.size())
            .log();

        return Response.ok(items).build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics/{id}
    // -------------------------------------------------------------------------

    @GET
    @Path(ApiConstants.Paths.Diagnostics.BY_ID)
    public Response getDiagnostic(
            @PathParam(ApiConstants.Paths.Diagnostics.PATH_PARAM) String diagnosticId) {

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_GET_REQUEST)
            .field("diagnosticId", diagnosticId)
            .log();

        Optional<Diagnostic> found = diagnosticService.getDiagnosticById(diagnosticId);

        if (found.isEmpty()) {
            log.warn(LogMessages.Diagnostic.DIAGNOSTIC_GET_NOT_FOUND)
                .field("diagnosticId", diagnosticId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(404, "Not Found", "No diagnostic found with id: " + diagnosticId))
                .build();
        }

        Diagnostic diagnostic = found.get();
        Alert alert = alertRepository.findById(diagnostic.getAlertId()).orElse(null);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_GET_FOUND)
            .field("diagnosticId", diagnosticId)
            .log();

        return Response.ok(DiagnosticDetailResponse.from(diagnostic, alert, clusterName)).build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/diagnostics
    // -------------------------------------------------------------------------

    @POST
    public Response triggerDiagnostic(DiagnosticTriggerRequest request) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGER_REQUEST).log();

        AlertWebhookRequest webhookRequest = triggerMapper.toWebhookRequest(request);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGER_ACCEPTED)
            .field("namespace",    request != null ? request.getNamespace()    : null)
            .field("container",    request != null ? request.getContainer()    : null)
            .field("podName",      request != null ? request.getPodName()      : null)
            .field("workloadName", request != null ? request.getWorkloadName() : null)
            .log();

        return webhookController.receiveAlerts(webhookRequest);
    }
}
