package com.devops.agent.controller;

import com.devops.agent.model.DevOpsHealthCheckRequest;
import com.devops.agent.model.DevOpsHealthCheckResponse;
import com.devops.agent.service.DevOpsInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for DevOps AI-assisted health monitoring and failure prediction
 */
@RestController
@RequestMapping("/api/devops")
@RequiredArgsConstructor
@Slf4j
public class DevOpsInsightController {

    private final DevOpsInsightService devOpsInsightService;

    /**
     * Perform health check and failure prediction for a project
     *
     * @param request Contains projectId
     * @return Health check response with risk level, predictions, and recommendations
     */
    @PostMapping("/healthCheck")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DevOpsHealthCheckResponse> performHealthCheck(
            @Valid @RequestBody DevOpsHealthCheckRequest request) {

        log.info("Health check requested for project: {}", request.getProjectId());

        try {
            DevOpsHealthCheckResponse response = devOpsInsightService.performHealthCheck(request.getProjectId());
            log.info("Health check completed for project: {} with risk level: {}",
                    request.getProjectId(), response.getRiskLevel());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for health check: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error performing health check for project: {}", request.getProjectId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
